package com.microcare.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcare.common.CorrelationId;
import com.microcare.notification.entity.NotificationLog;
import com.microcare.notification.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Consumes {@code APPOINTMENT_CONFIRMED} events from the outbox and persists a
 * notification log entry (the hook point where email/SMS dispatch would happen).
 *
 * <p>The correlation id stamped by the outbox publisher is restored into the MDC
 * so the whole flow is traceable end-to-end, and is stored on the log row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationLogRepository notificationLogRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${app.rabbitmq.queue.notification:notification.queue}")
    @Transactional
    public void handleAppointmentConfirmed(Message message) {
        Object correlationId = message.getMessageProperties().getHeaders().get(CorrelationId.HEADER);
        CorrelationId.set(correlationId != null ? correlationId.toString() : null);

        String messageId = message.getMessageProperties().getMessageId();
        log.info("Received appointment confirmed event for notification, messageId={}", messageId);

        try {
            String payload = new String(message.getBody());
            Map<String, Object> data = objectMapper.readValue(payload,
                    new TypeReference<Map<String, Object>>() {});

            Long appointmentId = Long.valueOf(data.get("appointmentId").toString());
            Long patientId = Long.valueOf(data.get("patientId").toString());

            NotificationLog logEntry = NotificationLog.builder()
                    .appointmentId(appointmentId)
                    .patientId(patientId)
                    .eventType("APPOINTMENT_CONFIRMED")
                    .message("Your appointment " + appointmentId + " has been confirmed")
                    .channel(NotificationLog.Channel.EMAIL)
                    .correlationId(CorrelationId.get())
                    .build();

            notificationLogRepository.save(logEntry);

            log.info("Notification log recorded: id={}, appointmentId={}, patientId={}",
                    logEntry.getId(), appointmentId, patientId);
        } catch (Exception e) {
            log.error("Failed to record notification log, messageId={}: {}", messageId, e.getMessage(), e);
            throw new RuntimeException("Failed to record notification log", e);
        } finally {
            CorrelationId.clear();
        }
    }
}
