package com.invoiceiq.dto;

import jakarta.validation.constraints.NotBlank;

public record DisputeInvoiceRequest(
    @NotBlank(message = "A reason is required when disputing an invoice.")
    String reason
) {
}
