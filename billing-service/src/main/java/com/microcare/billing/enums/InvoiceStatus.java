package com.microcare.billing.enums;

/**
 * Represents the lifecycle status of an invoice.
 *
 * PENDING  - Created but not yet paid (awaiting payment).
 * PAID     - Successfully paid in full.
 * OVERDUE  - Past the due date without payment.
 */
public enum InvoiceStatus {
    PENDING,
    PAID,
    OVERDUE
}
