package com.microcare.billing.controller;

import com.microcare.billing.dto.InvoiceResponse;
import com.microcare.billing.dto.PayInvoiceRequest;
import com.microcare.billing.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for invoice operations.
 *
 * Endpoints:
 *   GET    /api/invoices/{id}           — Get a single invoice by ID
 *   GET    /api/invoices?patientId=     — Get all invoices for a patient
 *   PUT    /api/invoices/{id}/pay       — Pay an invoice
 */
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    /**
     * GET /api/invoices/{id}
     *
     * Retrieves a single invoice by its ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getInvoiceById(@PathVariable Long id) {
        InvoiceResponse response = invoiceService.getInvoiceById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/invoices?patientId={patientId}
     *
     * Retrieves all invoices for a given patient.
     * If no patientId is specified, returns all invoices (admin use).
     */
    @GetMapping
    public ResponseEntity<List<InvoiceResponse>> getInvoices(
            @RequestParam(required = false) Long patientId) {
        if (patientId != null) {
            return ResponseEntity.ok(invoiceService.getInvoicesByPatientId(patientId));
        }
        return ResponseEntity.ok(invoiceService.getAllInvoices());
    }

    /**
     * PUT /api/invoices/{id}/pay
     *
     * Marks an invoice as PAID.
     * Validates that the payment amount matches the invoice amount.
     */
    @PutMapping("/{id}/pay")
    public ResponseEntity<InvoiceResponse> payInvoice(
            @PathVariable Long id,
            @Valid @RequestBody PayInvoiceRequest request) {
        InvoiceResponse response = invoiceService.payInvoice(id, request);
        return ResponseEntity.ok(response);
    }
}
