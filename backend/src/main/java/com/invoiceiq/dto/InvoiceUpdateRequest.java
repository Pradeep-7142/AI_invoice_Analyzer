package com.invoiceiq.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceUpdateRequest(
    UUID vendorId,

    @Size(max = 100)
    String invoiceNumber,

    LocalDate invoiceDate,
    LocalDate dueDate,

    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code.")
    String currency,

    @DecimalMin(value = "0", message = "Subtotal cannot be negative.")
    BigDecimal subtotalAmount,

    @DecimalMin(value = "0", message = "Tax amount cannot be negative.")
    BigDecimal taxAmount,

    @DecimalMin(value = "0", message = "Discount amount cannot be negative.")
    BigDecimal discountAmount,

    @DecimalMin(value = "0", message = "Total amount cannot be negative.")
    BigDecimal totalAmount,

    String notes,

    @Valid
    List<InvoiceLineItemRequest> lineItems
) {
}
