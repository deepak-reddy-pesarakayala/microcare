package com.microcare.appointment.service;

import com.microcare.appointment.entity.OutboxEvent;
import com.microcare.appointment.repository.OutboxEventRepository;
import com.microcare.common.CorrelationId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Scheduled poller that implements the "publisher" half of the Transactional Outbox Pattern.
 *
 * HOW IT WORKS:
 * -------------
 * 1. Runs on a fixed-delay schedule (default 5 seconds).
 * 2. Queries the outbox_events table for all rows with status = PENDING.
 * 3. For each PENDING event, it publishes the payload to the configured RabbitMQ exchange
 *    with the appropriate routing key, setting a "message-id" header for idempotency.
 * 4. On successful publish, marks the event as PUBLISHED.
 * 5. On failure, the event remains PENDING and will be retried on the next poll cycle.
 *
 * WHY THIS ELIMINATES THE DUAL-WRITE PROBLEM:
 * --------------------------------------------
 * The AppointmentService writes the OutboxEvent in the SAME database transaction
 * as the appointment status update. The poller runs in a SEPARATE transaction
 * boundary, ensuring that:
 *   - If the application crashes after the DB tx commits but before the poller
 *     publishes, the event is still in the DB as PENDING and will be picked up
 *     on restart.
 *   - If RabbitMQ is temporarily unavailable, events accumulate safely in the DB
 *     and are published when connectivity is restored.
 *   - Message ordering within an aggregate can be preserved by ordering by createdAt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange:appointment.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.routing-key.appointment-confirmed:appointment.confirmed}")
    private String appointmentConfirmedRoutingKey;

    /**
     * Polls for PENDING outbox events every 5 seconds (fixed delay).
     * Uses a separate transaction so that each publish attempt is committed independently
     * of the outbox write transaction.
     */
    @Scheduled(fixedDelayString = "${app.outbox.poller.fixed-delay-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxEvent.OutboxStatus.PENDING);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Outbox poller found {} PENDING event(s) to publish", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                publishEvent(event);
                event.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);
                outboxEventRepository.save(event);
                log.info("Published outbox event: id={}, type={}, aggregateId={}, messageId={}",
                        event.getId(), event.getEventType(), event.getAggregateId(), event.getMessageId());
            } catch (Exception e) {
                // Increment retry count for observability
                event.setRetryCount(event.getRetryCount() + 1);
                outboxEventRepository.save(event);
                log.error("Failed to publish outbox event id={} (attempt {}): {}",
                        event.getId(), event.getRetryCount(), e.getMessage(), e);
            }
        }
    }

    /**
     * Publishes a single outbox event to RabbitMQ with idempotency and trace headers.
     */
    private void publishEvent(OutboxEvent event) {
        String routingKey = resolveRoutingKey(event.getEventType());

        // The poller runs on a scheduled thread, so the MDC has no request context;
        // restore the correlation id captured at event-write time for traceable logs.
        CorrelationId.set(event.getCorrelationId());

        try {
            Message message = MessageBuilder
                    .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setMessageId(event.getMessageId())
                    .setHeader("eventType", event.getEventType())
                    .setHeader("aggregateType", event.getAggregateType())
                    .setHeader("aggregateId", String.valueOf(event.getAggregateId()))
                    .setHeader(CorrelationId.HEADER, event.getCorrelationId())
                    .build();

            rabbitTemplate.send(exchangeName, routingKey, message);

            log.debug("Sent message to exchange={}, routingKey={}, messageId={}",
                    exchangeName, routingKey, event.getMessageId());
        } finally {
            CorrelationId.clear();
        }
    }

    /**
     * Resolves the RabbitMQ routing key based on the event type.
     * Extensible for future event types (cancelled, rescheduled).
     */
    private String resolveRoutingKey(String eventType) {
        return switch (eventType) {
            case "APPOINTMENT_CONFIRMED" -> appointmentConfirmedRoutingKey;
            default -> {
                log.warn("Unknown event type: {}, using default routing key", eventType);
                yield appointmentConfirmedRoutingKey;
            }
        };
    }
}
