package com.microcare.billing.repository;

import com.microcare.billing.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Invoice entity.
 */
@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    /**
     * Find an invoice by its associated appointment ID.
     * Used for idempotent insert — if an invoice already exists for this
     * appointment, we skip creation (see InvoiceEventListener).
     */
    Optional<Invoice> findByAppointmentId(Long appointmentId);

    /**
     * Find all invoices for a given patient.
     */
    List<Invoice> findByPatientId(Long patientId);
}
