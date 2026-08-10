package com.invoiceiq.dto;

import java.math.BigDecimal;

public record BudgetHistoryPoint(String month, BigDecimal actualSpend, BigDecimal monthlyLimit, boolean overBudget) {
}
