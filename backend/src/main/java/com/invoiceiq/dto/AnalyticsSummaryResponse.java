package com.invoiceiq.dto;

import com.invoiceiq.entity.InvoiceStatus;
import java.math.BigDecimal;
import java.util.Map;

public record AnalyticsSummaryResponse(
    int periodMonths,
    BigDecimal totalSpend,
    int invoiceCount,
    BigDecimal averageInvoiceAmount,
    BigDecimal totalOutstanding,
    Map<InvoiceStatus, Long> statusCounts
) {
}
