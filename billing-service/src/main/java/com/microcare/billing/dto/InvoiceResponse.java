package com.microcare.billing.dto;

import com.microcare.billing.enums.InvoiceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO returned by the REST API for invoice queries.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceResponse {

    private Long id;
    private Long appointmentId;
    private Long patientId;
    private Double amount;
    private String currency;
    private InvoiceStatus status;
    private LocalDate dueDate;
    private LocalDateTime createdAt;
}
