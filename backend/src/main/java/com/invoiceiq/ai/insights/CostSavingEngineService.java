package com.invoiceiq.ai.insights;

import com.invoiceiq.analytics.AnalyticsService;
import com.invoiceiq.dto.AiIntelligenceDto.CostSavingRecommendation;
import com.invoiceiq.dto.BudgetStatusResponse;
import com.invoiceiq.dto.CategorySpendResponse;
import com.invoiceiq.dto.VendorSpendResponse;
import com.invoiceiq.entity.Invoice;
import com.invoiceiq.entity.InvoiceStatus;
import com.invoiceiq.entity.Vendor;
import com.invoiceiq.repository.InvoiceRepository;
import com.invoiceiq.security.CurrentUser;
import com.invoiceiq.service.BudgetService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CostSavingEngineService {

    private final AnalyticsService analyticsService;
    private final BudgetService budgetService;
    private final InvoiceRepository invoiceRepository;
    private final CurrentUser currentUser;

    public CostSavingEngineService(
        AnalyticsService analyticsService,
        BudgetService budgetService,
        InvoiceRepository invoiceRepository,
        CurrentUser currentUser
    ) {
        this.analyticsService = analyticsService;
        this.budgetService = budgetService;
        this.invoiceRepository = invoiceRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<CostSavingRecommendation> generateRecommendations() {
        List<CostSavingRecommendation> recommendations = new ArrayList<>();
        UUID organizationId = currentUser.organizationId();

        // 1. Budget Overrun Projections
        detectBudgetPacingOverruns(recommendations);

        // 2. Vendor Spend Concentration & Price Creep
        detectVendorOpportunities(recommendations, organizationId);

        // 3. Category Consolidation & Duplicate Subscriptions
        detectCategoryConsolidation(recommendations);

        return recommendations;
    }

    private void detectBudgetPacingOverruns(List<CostSavingRecommendation> recs) {
        YearMonth currentMonth = YearMonth.now();
        int daysInMonth = currentMonth.lengthOfMonth();
        int currentDay = LocalDate.now().getDayOfMonth();
        double monthProgress = (double) currentDay / daysInMonth;

        List<BudgetStatusResponse> budgets = budgetService.status(currentMonth);
        for (BudgetStatusResponse b : budgets) {
            if (b.monthlyLimit().compareTo(BigDecimal.ZERO) <= 0) continue;

            double expectedUsage = monthProgress * 100.0;
            if (b.percentUsed() > expectedUsage + 25.0 && !b.overBudget()) {
                BigDecimal projectedTotal = b.actualSpend().divide(BigDecimal.valueOf(monthProgress), 2, RoundingMode.HALF_UP);
                BigDecimal projectedOverrun = projectedTotal.subtract(b.monthlyLimit());

                recs.add(new CostSavingRecommendation(
                    "BUDGET-" + b.category().toLowerCase().replace(" ", "-"),
                    "High Spend Velocity in " + b.category(),
                    b.category(),
                    String.format("Day %d of month: already consumed %.1f%% of ₹%s budget limit (expected ~%.1f%%).",
                        currentDay, b.percentUsed(), b.monthlyLimit(), expectedUsage),
                    projectedOverrun.multiply(BigDecimal.valueOf(12)),
                    "HIGH",
                    "Freeze discretionary approvals in " + b.category() + " until next billing cycle.",
                    "BUDGET_OVERRUN"
                ));
            }
        }
    }

    private void detectVendorOpportunities(List<CostSavingRecommendation> recs, UUID organizationId) {
        List<VendorSpendResponse> topVendors = analyticsService.topVendors(6, 10);
        BigDecimal totalSpend = topVendors.stream()
            .map(VendorSpendResponse::totalSpend)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalSpend.compareTo(BigDecimal.ZERO) <= 0) return;

        for (VendorSpendResponse v : topVendors) {
            // Check spend concentration (> 35% of total spend)
            double concentration = v.totalSpend().divide(totalSpend, 4, RoundingMode.HALF_UP).doubleValue() * 100.0;
            if (concentration >= 35.0) {
                BigDecimal estSavings = v.totalSpend().multiply(BigDecimal.valueOf(0.08)).setScale(2, RoundingMode.HALF_UP);
                recs.add(new CostSavingRecommendation(
                    "CONC-" + v.vendorId(),
                    "High Spend Concentration: " + v.vendorName(),
                    v.category() != null ? v.category() : "General",
                    String.format("%s accounts for %.1f%% (₹%s) of all organization spend over the past 6 months.",
                        v.vendorName(), concentration, v.totalSpend()),
                    estSavings.multiply(BigDecimal.valueOf(2)),
                    "MEDIUM",
                    "Negotiate volume enterprise discount or lock in a multi-year fixed SLA rate (typical 8-12% savings).",
                    "CONCENTRATION_RISK"
                ));
            }
        }
    }

    private void detectCategoryConsolidation(List<CostSavingRecommendation> recs) {
        UUID organizationId = currentUser.organizationId();
        List<Invoice> activeInvoices = invoiceRepository.findByOrganizationId(organizationId).stream()
            .filter(i -> i.getStatus() != InvoiceStatus.ARCHIVED && i.getVendor() != null)
            .toList();

        Map<String, List<Vendor>> vendorsByCategory = activeInvoices.stream()
            .filter(i -> i.getVendor().getCategory() != null && !i.getVendor().getCategory().isBlank())
            .collect(Collectors.groupingBy(
                i -> i.getVendor().getCategory(),
                Collectors.mapping(Invoice::getVendor, Collectors.toList())
            ));

        vendorsByCategory.forEach((category, vendorList) -> {
            long distinctVendors = vendorList.stream().map(Vendor::getId).distinct().count();
            if (distinctVendors >= 3 && ("Software".equalsIgnoreCase(category) || "Cloud".equalsIgnoreCase(category) || "Marketing".equalsIgnoreCase(category))) {
                recs.add(new CostSavingRecommendation(
                    "CONSOL-" + category.toLowerCase(),
                    "Vendor Redundancy in " + category,
                    category,
                    String.format("Found %d different active vendors in '%s'. Tool overlap often creates redundant SaaS seat licensing.",
                        distinctVendors, category),
                    new BigDecimal("120000.00"),
                    "HIGH",
                    "Conduct a SaaS license audit to eliminate duplicate subscriptions and consolidate seats.",
                    "DUPLICATE_SUBSCRIPTION"
                ));
            }
        });
    }
}
