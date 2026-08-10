package com.invoiceiq.service;

import com.invoiceiq.audit.AuditLogService;
import com.invoiceiq.dto.BudgetHistoryPoint;
import com.invoiceiq.dto.BudgetHistoryResponse;
import com.invoiceiq.dto.BudgetRequest;
import com.invoiceiq.dto.BudgetResponse;
import com.invoiceiq.dto.BudgetStatusResponse;
import com.invoiceiq.entity.Budget;
import com.invoiceiq.entity.Invoice;
import com.invoiceiq.entity.InvoiceStatus;
import com.invoiceiq.entity.Organization;
import com.invoiceiq.entity.UserAccount;
import com.invoiceiq.exception.BusinessValidationException;
import com.invoiceiq.exception.ResourceNotFoundException;
import com.invoiceiq.repository.BudgetRepository;
import com.invoiceiq.repository.InvoiceRepository;
import com.invoiceiq.repository.OrganizationRepository;
import com.invoiceiq.repository.UserAccountRepository;
import com.invoiceiq.security.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monthly category budgets. A budget is a recurring cap ("Marketing gets
 * 50,000/month"), not a one-off allocation, so it's a single row applied
 * to whichever month you ask about — see {@link Budget}. Actual spend is
 * computed on read from real invoice totals for that month, never cached,
 * the same "recompute, don't persist" choice made for the Phase 5 risk
 * signals.
 */
@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final InvoiceRepository invoiceRepository;
    private final OrganizationRepository organizationRepository;
    private final UserAccountRepository userAccountRepository;
    private final CurrentUser currentUser;
    private final AuditLogService auditLogService;

    public BudgetService(
        BudgetRepository budgetRepository,
        InvoiceRepository invoiceRepository,
        OrganizationRepository organizationRepository,
        UserAccountRepository userAccountRepository,
        CurrentUser currentUser,
        AuditLogService auditLogService
    ) {
        this.budgetRepository = budgetRepository;
        this.invoiceRepository = invoiceRepository;
        this.organizationRepository = organizationRepository;
        this.userAccountRepository = userAccountRepository;
        this.currentUser = currentUser;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> list() {
        return budgetRepository.findByOrganizationIdOrderByCategoryAsc(currentUser.organizationId())
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<BudgetStatusResponse> status(YearMonth month) {
        UUID organizationId = currentUser.organizationId();
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        List<Invoice> invoicesInMonth = invoiceRepository.findByOrganizationIdAndInvoiceDateBetween(organizationId, start, end)
            .stream()
            .filter(i -> i.getStatus() != InvoiceStatus.ARCHIVED)
            .filter(i -> i.getVendor() != null && i.getVendor().getCategory() != null)
            .toList();

        return budgetRepository.findByOrganizationIdOrderByCategoryAsc(organizationId).stream()
            .map(budget -> toStatusResponse(budget, invoicesInMonth))
            .toList();
    }

    private BudgetStatusResponse toStatusResponse(Budget budget, List<Invoice> invoicesInMonth) {
        List<Invoice> matching = invoicesInMonth.stream()
            .filter(i -> i.getVendor().getCategory().equalsIgnoreCase(budget.getCategory()))
            .toList();

        BigDecimal actualSpend = matching.stream()
            .map(Invoice::getTotalAmount)
            .filter(a -> a != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remaining = budget.getMonthlyLimit().subtract(actualSpend);
        double percentUsed = budget.getMonthlyLimit().compareTo(BigDecimal.ZERO) == 0
            ? 0
            : actualSpend.divide(budget.getMonthlyLimit(), 4, RoundingMode.HALF_UP).doubleValue() * 100;

        return new BudgetStatusResponse(
            budget.getId(),
            budget.getCategory(),
            budget.getMonthlyLimit(),
            budget.getCurrency(),
            actualSpend,
            remaining,
            Math.round(percentUsed * 10) / 10.0,
            actualSpend.compareTo(budget.getMonthlyLimit()) > 0,
            matching.size()
        );
    }

    /** Per-category actual-vs-limit for each of the last {@code months} months, for a budget trend chart. */
    @Transactional(readOnly = true)
    public List<BudgetHistoryResponse> history(int months) {
        UUID organizationId = currentUser.organizationId();
        List<Budget> budgets = budgetRepository.findByOrganizationIdOrderByCategoryAsc(organizationId);
        List<YearMonth> monthsBack = java.util.stream.IntStream.range(0, months)
            .mapToObj(i -> YearMonth.now().minusMonths(months - 1L - i))
            .toList();

        Map<YearMonth, List<Invoice>> invoicesByMonth = new LinkedHashMap<>();
        for (YearMonth month : monthsBack) {
            List<Invoice> invoicesInMonth = invoiceRepository.findByOrganizationIdAndInvoiceDateBetween(
                    organizationId, month.atDay(1), month.atEndOfMonth())
                .stream()
                .filter(i -> i.getStatus() != InvoiceStatus.ARCHIVED)
                .filter(i -> i.getVendor() != null && i.getVendor().getCategory() != null)
                .toList();
            invoicesByMonth.put(month, invoicesInMonth);
        }

        return budgets.stream()
            .map(budget -> {
                List<BudgetHistoryPoint> points = monthsBack.stream()
                    .map(month -> {
                        BigDecimal actualSpend = invoicesByMonth.get(month).stream()
                            .filter(i -> i.getVendor().getCategory().equalsIgnoreCase(budget.getCategory()))
                            .map(Invoice::getTotalAmount)
                            .filter(a -> a != null)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                        return new BudgetHistoryPoint(month.toString(), actualSpend, budget.getMonthlyLimit(),
                            actualSpend.compareTo(budget.getMonthlyLimit()) > 0);
                    })
                    .toList();
                return new BudgetHistoryResponse(budget.getId(), budget.getCategory(), budget.getCurrency(), points);
            })
            .toList();
    }

    @Transactional
    public BudgetResponse create(BudgetRequest request) {
        UUID organizationId = currentUser.organizationId();
        budgetRepository.findByOrganizationIdAndCategoryIgnoreCase(organizationId, request.category())
            .ifPresent(existing -> {
                throw new BusinessValidationException("A budget for category \"" + request.category() + "\" already exists.");
            });

        Organization organization = organizationRepository.getReferenceById(organizationId);
        Budget budget = new Budget(organization, request.category(), request.monthlyLimit(),
            request.currency() == null ? "INR" : request.currency());
        budgetRepository.save(budget);

        auditLogService.record(organization, currentUserAccount(), "budget.created", "Budget",
            budget.getId().toString(), Map.of("category", budget.getCategory(), "monthlyLimit", budget.getMonthlyLimit().toString()));

        return toResponse(budget);
    }

    @Transactional
    public BudgetResponse update(UUID budgetId, BudgetRequest request) {
        Budget budget = findOwned(budgetId);
        budget.setMonthlyLimit(request.monthlyLimit());
        if (request.currency() != null) {
            budget.setCurrency(request.currency());
        }

        auditLogService.record(budget.getOrganization(), currentUserAccount(), "budget.updated", "Budget",
            budget.getId().toString(), Map.of("monthlyLimit", budget.getMonthlyLimit().toString()));

        return toResponse(budget);
    }

    @Transactional
    public void delete(UUID budgetId) {
        Budget budget = findOwned(budgetId);
        auditLogService.record(budget.getOrganization(), currentUserAccount(), "budget.deleted", "Budget",
            budget.getId().toString(), Map.of("category", budget.getCategory()));
        budgetRepository.delete(budget);
    }

    private Budget findOwned(UUID budgetId) {
        return budgetRepository.findByIdAndOrganizationId(budgetId, currentUser.organizationId())
            .orElseThrow(() -> new ResourceNotFoundException("Budget not found."));
    }

    private UserAccount currentUserAccount() {
        return userAccountRepository.getReferenceById(currentUser.userId());
    }

    private BudgetResponse toResponse(Budget budget) {
        return new BudgetResponse(
            budget.getId(), budget.getCategory(), budget.getMonthlyLimit(), budget.getCurrency(),
            budget.getCreatedAt(), budget.getUpdatedAt());
    }
}
