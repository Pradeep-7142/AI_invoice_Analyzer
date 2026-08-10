package com.invoiceiq.dto;

import java.math.BigDecimal;

public record MonthlySpendPoint(String month, BigDecimal totalSpend, int invoiceCount) {
}
