package com.invoiceiq.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BudgetStatusResponse(
    UUID budgetId,
    String category,
    BigDecimal monthlyLimit,
    String currency,
    BigDecimal actualSpend,
    BigDecimal remaining,
    double percentUsed,
    boolean overBudget,
    int invoiceCount
) {
}
