package com.invoiceiq.dto;

import com.invoiceiq.entity.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class AiIntelligenceDto {

    public record NaturalSearchRequest(
        String query
    ) {}

    public record NaturalSearchCriteria(
        String vendorName,
        String category,
        InvoiceStatus status,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        LocalDate fromDate,
        LocalDate toDate,
        String interpretation
    ) {}

    public record NaturalSearchResponse(
        String query,
        NaturalSearchCriteria criteria,
        List<InvoiceSummaryResponse> results,
        int totalMatches
    ) {}

    public record CostSavingRecommendation(
        String id,
        String title,
        String category,
        String evidence,
        BigDecimal estimatedAnnualSaving,
        String confidence, // "HIGH", "MEDIUM", "LOW"
        String recommendedAction,
        String type // "PRICE_CREEP", "BUDGET_OVERRUN", "CONCENTRATION_RISK", "DUPLICATE_SUBSCRIPTION"
    ) {}
}
