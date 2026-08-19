package com.microcare.billing.entity;

import com.microcare.billing.enums.InvoiceStatus;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Invoice entity — represents a bill generated for a confirmed appointment.
 *
 * Each invoice is uniquely tied to an appointmentId to prevent duplicate billing.
 * The billing-service creates a PENDING invoice when it consumes an
 * "APPOINTMENT_CONFIRMED" event from the transactional outbox.
 */
@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The appointment that triggered this invoice.
     * Uniquely constrained to enforce idempotent inserts.
     */
    @Column(name = "appointment_id", nullable = false, unique = true)
    private Long appointmentId;

    /**
     * The patient responsible for this invoice.
     */
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    /**
     * The invoice amount (e.g., consultation fee).
     */
    @Column(name = "amount", nullable = false)
    private Double amount;

    /**
     * Currency code (e.g., USD, EUR).
     */
    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    /**
     * Current status of the invoice.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.PENDING;

    /**
     * Date by which the invoice should be paid.
     */
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (dueDate == null) {
            // Default due date: 30 days from creation
            dueDate = LocalDate.now().plusDays(30);
        }
    }
}
