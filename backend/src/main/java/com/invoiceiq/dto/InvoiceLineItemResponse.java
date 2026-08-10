package com.invoiceiq.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceLineItemResponse(
    UUID id,
    int lineOrder,
    String description,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal taxAmount,
    BigDecimal discountAmount,
    BigDecimal totalAmount
) {
}
