package com.invoiceiq.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record InvoiceLineItemRequest(
    @NotBlank(message = "Line item description is required.")
    String description,

    @NotNull(message = "Quantity is required.")
    @DecimalMin(value = "0.001", message = "Quantity must be greater than zero.")
    BigDecimal quantity,

    @NotNull(message = "Unit price is required.")
    @DecimalMin(value = "0", message = "Unit price cannot be negative.")
    BigDecimal unitPrice,

    @DecimalMin(value = "0", message = "Tax amount cannot be negative.")
    BigDecimal taxAmount,

    @DecimalMin(value = "0", message = "Discount amount cannot be negative.")
    BigDecimal discountAmount,

    @NotNull(message = "Line total is required.")
    @DecimalMin(value = "0", message = "Line total cannot be negative.")
    BigDecimal totalAmount
) {
}
