package com.invoiceiq.controller;

import com.invoiceiq.dto.CashFlowForecastResponse;
import com.invoiceiq.dto.MonthlyProjectionResponse;
import com.invoiceiq.forecast.ForecastService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/forecast")
@PreAuthorize("hasAnyAuthority('ORGANIZATION_ADMIN', 'FINANCE_MANAGER', 'ACCOUNTANT', 'VIEWER')")
public class ForecastController {

    private final ForecastService forecastService;

    public ForecastController(ForecastService forecastService) {
        this.forecastService = forecastService;
    }

    @GetMapping("/cash-flow")
    public CashFlowForecastResponse cashFlow(@RequestParam(defaultValue = "8") int weeks) {
        return forecastService.cashFlow(weeks);
    }

    @GetMapping("/monthly-projection")
    public MonthlyProjectionResponse monthlyProjection(@RequestParam(defaultValue = "3") int months) {
        return forecastService.monthlyProjection(months);
    }
}
