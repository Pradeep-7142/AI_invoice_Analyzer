package com.invoiceiq.dto;

import com.invoiceiq.entity.PaymentMethod;
import com.invoiceiq.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A payment plus enough invoice/vendor context to render org-wide payment lists without a second lookup. */
public record PaymentSummaryDto(
    UUID id,
    UUID invoiceId,
    String invoiceNumber,
    String vendorName,
    BigDecimal amount,
    String currency,
    PaymentMethod method,
    PaymentStatus status,
    LocalDate scheduledDate,
    Instant completedAt,
    Instant createdAt
) {
}
