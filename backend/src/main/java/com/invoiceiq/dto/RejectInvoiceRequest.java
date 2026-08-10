package com.invoiceiq.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectInvoiceRequest(
    @NotBlank(message = "A reason is required when rejecting an invoice.")
    String reason
) {
}
