package com.invoiceiq.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DashboardSummaryDto(
    long totalInvoices,
    long needsReviewCount,
    long verifiedCount,
    long approvedCount,
    long rejectedCount,
    BigDecimal totalSpend,
    BigDecimal pendingSpend,
    List<InvoiceSummaryResponse> recentInvoices,
    List<VendorSpendDto> topVendors,
    List<MonthlyTrendDto> monthlyTrends,
    Map<String, Long> statusBreakdown
) {
    public record VendorSpendDto(String vendorName, BigDecimal totalAmount, long invoiceCount) {}
    public record MonthlyTrendDto(String month, BigDecimal totalAmount, long count) {}
}
