package com.invoiceiq.service;

import com.invoiceiq.dto.ActionCenterResponse;
import com.invoiceiq.dto.BudgetStatusResponse;
import com.invoiceiq.dto.InvoiceSummaryResponse;
import com.invoiceiq.dto.VendorSummaryDto;
import com.invoiceiq.entity.Invoice;
import com.invoiceiq.entity.InvoiceStatus;
import com.invoiceiq.entity.OrgRole;
import com.invoiceiq.repository.InvoiceApprovalRepository;
import com.invoiceiq.repository.InvoiceRepository;
import com.invoiceiq.security.CurrentUser;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The home dashboard's "needs a human right now" list. Deliberately not a
 * stored notification feed with its own read/unread state — every section
 * is a live query against data that already exists (mirroring every other
 * phase's "recompute, don't persist" choice), so it can never go stale and
 * needed no new table.
 */
@Service
public class DashboardService {

    private static final int MAX_ITEMS_PER_SECTION = 10;

    private final InvoiceRepository invoiceRepository;
    private final InvoiceApprovalRepository invoiceApprovalRepository;
    private final BudgetService budgetService;
    private final CurrentUser currentUser;

    public DashboardService(
        InvoiceRepository invoiceRepository,
        InvoiceApprovalRepository invoiceApprovalRepository,
        BudgetService budgetService,
        CurrentUser currentUser
    ) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceApprovalRepository = invoiceApprovalRepository;
        this.budgetService = budgetService;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public ActionCenterResponse actionCenter() {
        UUID organizationId = currentUser.organizationId();
        OrgRole role = currentUser.role();

        List<InvoiceSummaryResponse> pendingMyApproval = pendingMyApproval(organizationId, role);
        List<InvoiceSummaryResponse> needsMyAttention = needsMyAttention(organizationId);
        List<InvoiceSummaryResponse> overdueInvoices = overdueInvoices(organizationId, role);
        List<BudgetStatusResponse> overBudgetCategories = budgetService.status(YearMonth.now()).stream()
            .filter(BudgetStatusResponse::overBudget)
            .toList();

        return new ActionCenterResponse(pendingMyApproval, needsMyAttention, overdueInvoices, overBudgetCategories);
    }

    /** Only a manager/admin can decide anything, and never their own submission — same rule InvoiceService.approve() enforces. */
    private List<InvoiceSummaryResponse> pendingMyApproval(UUID organizationId, OrgRole role) {
        if (role != OrgRole.ORGANIZATION_ADMIN && role != OrgRole.FINANCE_MANAGER) {
            return List.of();
        }
        return invoiceRepository.findByOrganizationIdAndStatus(organizationId, InvoiceStatus.PENDING_APPROVAL).stream()
            .filter(invoice -> !invoice.getSubmittedBy().getId().equals(currentUser.userId()))
            .filter(invoice -> canDecide(role, invoice))
            .map(this::toSummary)
            .limit(MAX_ITEMS_PER_SECTION)
            .toList();
    }

    private boolean canDecide(OrgRole role, Invoice invoice) {
        OrgRole required = ApprovalPolicy.requiredApproverRole(invoice.getOrganization(), invoice.getTotalAmount());
        return required != OrgRole.ORGANIZATION_ADMIN || role == OrgRole.ORGANIZATION_ADMIN;
    }

    /** My own submissions that bounced back: disputed, or rejected and still sitting unfixed. */
    private List<InvoiceSummaryResponse> needsMyAttention(UUID organizationId) {
        return invoiceRepository.findByOrganizationIdAndSubmittedById(organizationId, currentUser.userId()).stream()
            .filter(invoice -> invoice.getStatus() == InvoiceStatus.DISPUTED
                || (invoice.getStatus() == InvoiceStatus.NEEDS_REVIEW && invoiceApprovalRepository.existsByInvoiceId(invoice.getId())))
            .map(this::toSummary)
            .limit(MAX_ITEMS_PER_SECTION)
            .toList();
    }

    /** Employees only ever see their own invoices anywhere in the app — the dashboard follows the same rule. */
    private List<InvoiceSummaryResponse> overdueInvoices(UUID organizationId, OrgRole role) {
        List<Invoice> overdue = role == OrgRole.EMPLOYEE
            ? invoiceRepository.findByOrganizationIdAndSubmittedByIdAndStatus(organizationId, currentUser.userId(), InvoiceStatus.OVERDUE)
            : invoiceRepository.findByOrganizationIdAndStatus(organizationId, InvoiceStatus.OVERDUE);
        return overdue.stream().map(this::toSummary).limit(MAX_ITEMS_PER_SECTION).toList();
    }

    private InvoiceSummaryResponse toSummary(Invoice invoice) {
        return new InvoiceSummaryResponse(
            invoice.getId(),
            invoice.getVendor() == null ? null : new VendorSummaryDto(invoice.getVendor().getId(), invoice.getVendor().getName()),
            invoice.getInvoiceNumber(),
            invoice.getInvoiceDate(),
            invoice.getDueDate(),
            invoice.getCurrency(),
            invoice.getTotalAmount(),
            invoice.getStatus(),
            invoice.getSubmittedBy().getFullName(),
            invoice.getCreatedAt()
        );
    }
}
