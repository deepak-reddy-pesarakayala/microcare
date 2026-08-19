package com.microcare.appointment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * OutboxEvent entity — the core of the Transactional Outbox Pattern.
 *
 * WHY THIS ELIMINATES THE DUAL-WRITE PROBLEM:
 * --------------------------------------------
 * Without an outbox, a service that updates its database AND publishes a message
 * to a message broker faces a dual-write problem: if the DB write succeeds but
 * the broker publish fails (or vice versa), the system becomes inconsistent.
 *
 * The outbox pattern solves this by writing the event to the SAME database
 * transaction as the business entity update. A separate poller then reads
 * PENDING events and publishes them to RabbitMQ reliably. This guarantees:
 *   - Exactly-once (at-least-once) delivery of the event
 *   - Atomicity: the business update and the event record succeed or fail together
 *   - Resilience: if RabbitMQ is down, events accumulate in the DB and are
 *     published when it recovers
 *   - Ordering: events can be sequenced within the aggregate
 */
@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * A unique identifier for the message, used by the consumer for idempotent processing.
     * Set on first publish attempt so retries carry the same ID.
     */
    @Column(name = "message_id", nullable = false, unique = true, length = 36)
    private String messageId;

    /**
     * The type of aggregate root that this event originated from (e.g., "appointment").
     */
    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    /**
     * The ID of the aggregate root instance.
     */
    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    /**
     * The type of event (e.g., "APPOINTMENT_CONFIRMED", "APPOINTMENT_CANCELLED").
     */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /**
     * JSON payload containing the event data (e.g., appointmentId, patientId, etc.).
     */
    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    /**
     * Current status: PENDING = not yet published, PUBLISHED = successfully sent to broker.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    /**
     * Number of publish attempts so far (for observability / dead-letter routing).
     */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    /**
     * Trace id captured from the HTTP request that created the event, forwarded
     * onto the RabbitMQ message so downstream consumers can correlate the whole
     * flow end-to-end.
     */
    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (messageId == null) {
            messageId = UUID.randomUUID().toString();
        }
        createdAt = LocalDateTime.now();
    }

    /**
     * Status enum specific to the outbox lifecycle.
     */
    public enum OutboxStatus {
        PENDING,
        PUBLISHED
    }
}
