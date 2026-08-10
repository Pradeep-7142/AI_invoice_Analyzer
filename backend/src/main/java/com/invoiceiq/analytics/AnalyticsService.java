package com.invoiceiq.analytics;

import com.invoiceiq.dto.AnalyticsSummaryResponse;
import com.invoiceiq.dto.CategorySpendResponse;
import com.invoiceiq.dto.MonthlySpendPoint;
import com.invoiceiq.dto.VendorSpendResponse;
import com.invoiceiq.entity.Budget;
import com.invoiceiq.entity.Invoice;
import com.invoiceiq.entity.InvoiceStatus;
import com.invoiceiq.entity.Payment;
import com.invoiceiq.entity.PaymentStatus;
import com.invoiceiq.entity.Vendor;
import com.invoiceiq.repository.BudgetRepository;
import com.invoiceiq.repository.InvoiceRepository;
import com.invoiceiq.repository.PaymentRepository;
import com.invoiceiq.security.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every number here is aggregated in Java from real invoice/payment rows on
 * every request — the same "recompute, never cache a derived number" choice
 * made for Phase 5's risk signals and Phase 6's budget status. At this
 * scale, a materialized reporting table would be premature: it's another
 * thing that can go stale, for a query that's already cheap.
 */
@Service
public class AnalyticsService {

    private static final Set<InvoiceStatus> PAYABLE_STATUSES = Set.of(
        InvoiceStatus.APPROVED, InvoiceStatus.PAYMENT_SCHEDULED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.OVERDUE);

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final BudgetRepository budgetRepository;
    private final CurrentUser currentUser;

    public AnalyticsService(
        InvoiceRepository invoiceRepository, PaymentRepository paymentRepository,
        BudgetRepository budgetRepository, CurrentUser currentUser
    ) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.budgetRepository = budgetRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summary(int months) {
        UUID organizationId = currentUser.organizationId();
        List<Invoice> periodInvoices = invoicesInPeriod(organizationId, months);

        BigDecimal totalSpend = sumAmounts(periodInvoices);
        int invoiceCount = periodInvoices.size();
        BigDecimal average = invoiceCount == 0
            ? BigDecimal.ZERO
            : totalSpend.divide(BigDecimal.valueOf(invoiceCount), 2, RoundingMode.HALF_UP);

        List<Invoice> allInvoices = invoiceRepository.findByOrganizationId(organizationId);
        Map<InvoiceStatus, Long> statusCounts = allInvoices.stream()
            .collect(Collectors.groupingBy(Invoice::getStatus, Collectors.counting()));

        Map<UUID, BigDecimal> completedPaidByInvoice = completedPaidByInvoice(organizationId);
        BigDecimal totalOutstanding = allInvoices.stream()
            .filter(i -> PAYABLE_STATUSES.contains(i.getStatus()))
            .map(i -> outstandingFor(i, completedPaidByInvoice))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new AnalyticsSummaryResponse(months, totalSpend, invoiceCount, average, totalOutstanding, statusCounts);
    }

    @Transactional(readOnly = true)
    public List<MonthlySpendPoint> spendTrend(int months) {
        UUID organizationId = currentUser.organizationId();
        List<Invoice> periodInvoices = invoicesInPeriod(organizationId, months);

        return monthsBackFromNow(months).stream()
            .map(month -> {
                List<Invoice> inMonth = periodInvoices.stream()
                    .filter(i -> YearMonth.from(i.getInvoiceDate()).equals(month))
                    .toList();
                return new MonthlySpendPoint(month.toString(), sumAmounts(inMonth), inMonth.size());
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public List<VendorSpendResponse> topVendors(int months, int limit) {
        UUID organizationId = currentUser.organizationId();
        List<Invoice> periodInvoices = invoicesInPeriod(organizationId, months);

        Map<Vendor, List<Invoice>> byVendor = periodInvoices.stream()
            .filter(i -> i.getVendor() != null)
            .collect(Collectors.groupingBy(Invoice::getVendor));

        return byVendor.entrySet().stream()
            .map(entry -> {
                Vendor vendor = entry.getKey();
                List<Invoice> invoices = entry.getValue();
                BigDecimal total = sumAmounts(invoices);
                BigDecimal average = invoices.isEmpty()
                    ? BigDecimal.ZERO
                    : total.divide(BigDecimal.valueOf(invoices.size()), 2, RoundingMode.HALF_UP);
                return new VendorSpendResponse(vendor.getId(), vendor.getName(), vendor.getCategory(), total, invoices.size(), average);
            })
            .sorted(Comparator.comparing(VendorSpendResponse::totalSpend).reversed())
            .limit(limit)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<CategorySpendResponse> categorySpend(int months) {
        UUID organizationId = currentUser.organizationId();
        List<Invoice> periodInvoices = invoicesInPeriod(organizationId, months);

        Map<String, List<Invoice>> byCategory = periodInvoices.stream()
            .filter(i -> i.getVendor() != null && i.getVendor().getCategory() != null && !i.getVendor().getCategory().isBlank())
            .collect(Collectors.groupingBy(i -> i.getVendor().getCategory()));

        return byCategory.entrySet().stream()
            .map(entry -> {
                String category = entry.getKey();
                List<Invoice> invoices = entry.getValue();
                BigDecimal budgetLimit = budgetRepository.findByOrganizationIdAndCategoryIgnoreCase(organizationId, category)
                    .map(Budget::getMonthlyLimit)
                    .orElse(null);
                return new CategorySpendResponse(category, sumAmounts(invoices), invoices.size(), budgetLimit);
            })
            .sorted(Comparator.comparing(CategorySpendResponse::totalSpend).reversed())
            .toList();
    }

    private List<Invoice> invoicesInPeriod(UUID organizationId, int months) {
        LocalDate start = YearMonth.now().minusMonths(months - 1L).atDay(1);
        return invoiceRepository.findByOrganizationIdAndInvoiceDateBetween(organizationId, start, LocalDate.now()).stream()
            .filter(i -> i.getStatus() != InvoiceStatus.ARCHIVED)
            .toList();
    }

    private List<YearMonth> monthsBackFromNow(int months) {
        YearMonth current = YearMonth.now();
        return java.util.stream.IntStream.range(0, months)
            .mapToObj(i -> current.minusMonths(months - 1L - i))
            .toList();
    }

    private BigDecimal sumAmounts(List<Invoice> invoices) {
        return invoices.stream()
            .map(Invoice::getTotalAmount)
            .filter(a -> a != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<UUID, BigDecimal> completedPaidByInvoice(UUID organizationId) {
        return paymentRepository.findByOrganizationId(organizationId).stream()
            .filter(p -> p.getStatus() == PaymentStatus.COMPLETED)
            .collect(Collectors.groupingBy(
                p -> p.getInvoice().getId(),
                Collectors.reducing(BigDecimal.ZERO, Payment::getAmount, BigDecimal::add)));
    }

    private BigDecimal outstandingFor(Invoice invoice, Map<UUID, BigDecimal> completedPaidByInvoice) {
        BigDecimal total = invoice.getTotalAmount() == null ? BigDecimal.ZERO : invoice.getTotalAmount();
        BigDecimal paid = completedPaidByInvoice.getOrDefault(invoice.getId(), BigDecimal.ZERO);
        BigDecimal outstanding = total.subtract(paid);
        return outstanding.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : outstanding;
    }
}
