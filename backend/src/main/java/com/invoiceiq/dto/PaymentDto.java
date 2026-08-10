package com.invoiceiq.dto;

import com.invoiceiq.entity.PaymentMethod;
import com.invoiceiq.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentDto(
    UUID id,
    BigDecimal amount,
    String currency,
    PaymentMethod method,
    PaymentStatus status,
    LocalDate scheduledDate,
    Instant completedAt,
    String reference,
    String notes,
    String recordedByName,
    Instant createdAt
) {
}
