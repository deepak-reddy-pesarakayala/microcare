package com.microcare.billing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request DTO for paying an invoice.
 * In a real system, this would contain payment details (card token, etc.).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayInvoiceRequest {

    /**
     * The payment amount — must match the invoice amount to prevent partial payments
     * (can be relaxed for partial payment support in the future).
     */
    @NotNull(message = "Payment amount is required")
    private Double amount;

    /**
     * Optional payment method identifier.
     */
    private String paymentMethod;
}
