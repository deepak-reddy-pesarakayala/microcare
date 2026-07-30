package com.microcare.billing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Listener for appointment events from the transactional outbox.
 *
 * HOW THIS ELIMINATES THE DUAL-WRITE PROBLEM:
 * --------------------------------------------
 * In a naive architecture, a service would:
 *   1. Update the appointment status in the DB.
 *   2. Publish a message to RabbitMQ directly.
 * If step 2 fails, the appointment is confirmed but no invoice is created (dual-write).
 *
 * The Transactional Outbox Pattern (implemented in appointment-service) fixes this:
 *   - The event is written to the outbox_events table IN THE SAME TRANSACTION
 *     as the appointment status update.
 *   - A scheduled poller reads PENDING events and publishes them to RabbitMQ
 *     with retry logic.
 *   - The billing-service consumer receives the event and creates an invoice.
 *
 * IDEMPOTENCY:
 * ------------
 * Each message carries a unique message-id header. The consumer checks
 * for an existing invoice by appointmentId (unique constraint) before
 * creating a new one. This ensures exactly-once processing semantics
 * even if the same message is delivered multiple times.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceEventListener {

    private final InvoiceService invoiceService;
    private final ObjectMapper objectMapper;

    /**
     * Consumes messages from the billing.invoice.queue.
     *
     * Retry Policy:
     *   - Max 3 attempts with exponential backoff (1s, 2s, 4s).
     *   - After exhaustion, message is routed to appointment.dlx → billing.invoice.dlq.
     *   - See RabbitMQConfig for the retry interceptor configuration.
     *
     * Idempotency:
     *   - The messageId header is logged for traceability.
     *   - InvoiceService.createInvoice() checks for existing invoices
     *     by appointmentId before inserting (unique constraint).
     */
    @RabbitListener(queues = "${app.rabbitmq.queue.billing-invoice:billing.invoice.queue}")
    public void handleAppointmentConfirmed(Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        log.info("Received appointment confirmed event, messageId={}", messageId);

        try {
            // Parse the JSON payload
            String payload = new String(message.getBody());
            Map<String, Object> data = objectMapper.readValue(payload,
                    new TypeReference<Map<String, Object>>() {});

            Long appointmentId = Long.valueOf(data.get("appointmentId").toString());
            Long patientId = Long.valueOf(data.get("patientId").toString());
            Double amount = Double.valueOf(data.get("amount").toString());
            String currency = (String) data.get("currency");

            log.debug("Processing invoice creation: appointmentId={}, patientId={}, amount={} {}",
                    appointmentId, patientId, amount, currency);

            // Idempotent invoice creation
            invoiceService.createInvoice(appointmentId, patientId, amount, currency);

            log.info("Successfully processed appointment confirmed event: messageId={}, appointmentId={}",
                    messageId, appointmentId);

        } catch (Exception e) {
            log.error("Failed to process appointment confirmed event, messageId={}: {}",
                    messageId, e.getMessage(), e);
            // Re-throw to trigger retry interceptor → DLQ after max retries
            throw new RuntimeException("Failed to process appointment confirmed event", e);
        }
    }
}
