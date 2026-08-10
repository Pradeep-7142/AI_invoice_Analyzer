package com.invoiceiq.dto;

import java.math.BigDecimal;

public record MonthlyProjectionResponse(BigDecimal averageMonthlySpend, int basedOnMonths, String explanation) {
}
