package com.invoiceiq.dto;

import com.invoiceiq.entity.InvoiceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceSummaryResponse(
    UUID id,
    VendorSummaryDto vendor,
    String invoiceNumber,
    LocalDate invoiceDate,
    LocalDate dueDate,
    String currency,
    BigDecimal totalAmount,
    InvoiceStatus status,
    String submittedByName,
    Instant createdAt
) {
}
