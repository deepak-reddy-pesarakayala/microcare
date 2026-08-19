package com.microcare.billing.service;

import com.microcare.billing.dto.InvoiceResponse;
import com.microcare.billing.dto.PayInvoiceRequest;
import com.microcare.billing.entity.Invoice;
import com.microcare.billing.enums.InvoiceStatus;
import com.microcare.billing.exception.InvoiceAlreadyPaidException;
import com.microcare.billing.exception.ResourceNotFoundException;
import com.microcare.billing.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service layer for invoice management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;

    /**
     * Creates a PENDING invoice from an outbox event payload.
     * This is called by the InvoiceEventListener (RabbitMQ consumer).
     *
     * IDEMPOTENCY GUARANTEE:
     * ----------------------
     * The appointmentId column has a UNIQUE constraint in the database.
     * If an invoice already exists for this appointment, we log a warning
     * and return the existing invoice. This ensures that even if the
     * consumer receives the same message twice (at-least-once delivery),
     * we never create duplicate invoices.
     *
     * @param appointmentId the ID of the confirmed appointment
     * @param patientId     the ID of the patient
     * @param amount        the invoice amount
     * @param currency      the currency code
     * @return the created (or existing) invoice
     */
    @Transactional
    public Invoice createInvoice(Long appointmentId, Long patientId, Double amount, String currency) {
        // Idempotent check: if invoice already exists for this appointment, skip
        var existingInvoice = invoiceRepository.findByAppointmentId(appointmentId);
        if (existingInvoice.isPresent()) {
            log.warn("Invoice already exists for appointmentId={}, skipping duplicate creation. Existing invoice id={}",
                    appointmentId, existingInvoice.get().getId());
            return existingInvoice.get();
        }

        Invoice invoice = Invoice.builder()
                .appointmentId(appointmentId)
                .patientId(patientId)
                .amount(amount)
                .currency(currency != null ? currency : "USD")
                .status(InvoiceStatus.PENDING)
                .build();

        Invoice saved = invoiceRepository.save(invoice);
        log.info("Invoice created: id={}, appointmentId={}, patientId={}, amount={} {}",
                saved.getId(), saved.getAppointmentId(), saved.getPatientId(),
                saved.getAmount(), saved.getCurrency());
        return saved;
    }

    /**
     * Retrieves an invoice by its ID.
     */
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceById(Long id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", id));
        return mapToResponse(invoice);
    }

    /**
     * Retrieves all invoices for a given patient.
     */
    @Transactional(readOnly = true)
    public List<InvoiceResponse> getInvoicesByPatientId(Long patientId) {
        return invoiceRepository.findByPatientId(patientId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Marks an invoice as PAID.
     *
     * @param id      the invoice ID
     * @param request the payment details (amount must match invoice amount)
     * @return the updated invoice response
     * @throws ResourceNotFoundException if the invoice doesn't exist
     * @throws InvoiceAlreadyPaidException if the invoice is already paid
     * @throws IllegalArgumentException if the payment amount doesn't match
     */
    @Transactional
    public InvoiceResponse payInvoice(Long id, PayInvoiceRequest request) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", id));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new InvoiceAlreadyPaidException(id);
        }

        // Validate payment amount matches invoice amount
        if (!invoice.getAmount().equals(request.getAmount())) {
            throw new IllegalArgumentException(
                    String.format("Payment amount %.2f does not match invoice amount %.2f",
                            request.getAmount(), invoice.getAmount()));
        }

        invoice.setStatus(InvoiceStatus.PAID);
        Invoice saved = invoiceRepository.save(invoice);
        log.info("Invoice paid: id={}, appointmentId={}, amount={} {}",
                saved.getId(), saved.getAppointmentId(), saved.getAmount(), saved.getCurrency());

        return mapToResponse(saved);
    }

    /**
     * Retrieves all invoices (admin/management use).
     */
    @Transactional(readOnly = true)
    public List<InvoiceResponse> getAllInvoices() {
        return invoiceRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    private InvoiceResponse mapToResponse(Invoice invoice) {
        return InvoiceResponse.builder()
                .id(invoice.getId())
                .appointmentId(invoice.getAppointmentId())
                .patientId(invoice.getPatientId())
                .amount(invoice.getAmount())
                .currency(invoice.getCurrency())
                .status(invoice.getStatus())
                .dueDate(invoice.getDueDate())
                .createdAt(invoice.getCreatedAt())
                .build();
    }
}
