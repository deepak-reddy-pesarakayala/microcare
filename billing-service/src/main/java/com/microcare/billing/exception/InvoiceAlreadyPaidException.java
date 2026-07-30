package com.microcare.billing.exception;

/**
 * Thrown when an attempt is made to pay an invoice that is already paid.
 */
public class InvoiceAlreadyPaidException extends RuntimeException {

    public InvoiceAlreadyPaidException(Long invoiceId) {
        super(String.format("Invoice with id '%s' is already paid", invoiceId));
    }
}
