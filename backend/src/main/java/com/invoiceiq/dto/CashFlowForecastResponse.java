package com.invoiceiq.dto;

import java.math.BigDecimal;
import java.util.List;

public record CashFlowForecastResponse(
    List<CashFlowWeekPoint> weeks,
    BigDecimal totalScheduled,
    BigDecimal totalDueUnscheduled
) {
}
