package com.invoiceiq.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CashFlowWeekPoint(LocalDate weekStart, BigDecimal scheduledAmount, BigDecimal dueUnscheduledAmount) {
}
