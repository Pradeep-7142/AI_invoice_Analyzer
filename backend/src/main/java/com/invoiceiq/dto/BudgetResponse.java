package com.invoiceiq.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BudgetResponse(
    UUID id,
    String category,
    BigDecimal monthlyLimit,
    String currency,
    Instant createdAt,
    Instant updatedAt
) {
}
