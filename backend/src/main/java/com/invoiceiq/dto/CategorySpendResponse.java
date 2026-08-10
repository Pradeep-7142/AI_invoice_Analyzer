package com.invoiceiq.dto;

import java.math.BigDecimal;

public record CategorySpendResponse(
    String category,
    BigDecimal totalSpend,
    int invoiceCount,
    BigDecimal budgetLimit
) {
}
