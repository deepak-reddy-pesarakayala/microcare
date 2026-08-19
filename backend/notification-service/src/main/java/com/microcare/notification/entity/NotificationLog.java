package com.microcare.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Persisted notification log entry — in a real deployment this row would drive
 * the actual delivery (email/SMS). It records enough information to audit and
 * replay notifications, and stores the correlation id for end-to-end tracing.
 */
@Entity
@Table(name = "notification_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

    public enum Channel {
        EMAIL,
        SMS,
        PUSH
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appointment_id", nullable = false)
    private Long appointmentId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    /** e.g. APPOINTMENT_CONFIRMED */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 10)
    @Builder.Default
    private Channel channel = Channel.EMAIL;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
