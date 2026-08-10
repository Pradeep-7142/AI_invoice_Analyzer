package com.invoiceiq.controller;

import com.invoiceiq.analytics.AnalyticsService;
import com.invoiceiq.dto.AnalyticsSummaryResponse;
import com.invoiceiq.dto.CategorySpendResponse;
import com.invoiceiq.dto.MonthlySpendPoint;
import com.invoiceiq.dto.VendorSpendResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only financial visibility — open to every role except EMPLOYEE, who only sees their own invoices elsewhere. */
@RestController
@RequestMapping("/api/analytics")
@PreAuthorize("hasAnyAuthority('ORGANIZATION_ADMIN', 'FINANCE_MANAGER', 'ACCOUNTANT', 'VIEWER')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    public AnalyticsSummaryResponse summary(@RequestParam(defaultValue = "6") int months) {
        return analyticsService.summary(months);
    }

    @GetMapping("/spend-trend")
    public List<MonthlySpendPoint> spendTrend(@RequestParam(defaultValue = "6") int months) {
        return analyticsService.spendTrend(months);
    }

    @GetMapping("/vendors/top")
    public List<VendorSpendResponse> topVendors(
        @RequestParam(defaultValue = "6") int months,
        @RequestParam(defaultValue = "10") int limit
    ) {
        return analyticsService.topVendors(months, limit);
    }

    @GetMapping("/categories")
    public List<CategorySpendResponse> categories(@RequestParam(defaultValue = "6") int months) {
        return analyticsService.categorySpend(months);
    }
}
