package com.invoiceiq.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * One immutable row per approve/reject decision — an audit trail
 * separate from AuditLog because this one is queried structurally
 * (rendered as the invoice's approval history), not just for compliance
 * export.
 */
@Entity
@Table(name = "invoice_approvals")
public class InvoiceApproval extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ApprovalDecisionType decision;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_role", nullable = false, length = 32)
    private OrgRole requiredRole;

    @Column(name = "threshold_amount", precision = 14, scale = 2)
    private BigDecimal thresholdAmount;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decided_by_user_id", nullable = false)
    private UserAccount decidedBy;

    protected InvoiceApproval() {
    }

    public InvoiceApproval(
        Invoice invoice, Organization organization, ApprovalDecisionType decision,
        OrgRole requiredRole, BigDecimal thresholdAmount, String reason, UserAccount decidedBy
    ) {
        this.invoice = invoice;
        this.organization = organization;
        this.decision = decision;
        this.requiredRole = requiredRole;
        this.thresholdAmount = thresholdAmount;
        this.reason = reason;
        this.decidedBy = decidedBy;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public Organization getOrganization() {
        return organization;
    }

    public ApprovalDecisionType getDecision() {
        return decision;
    }

    public OrgRole getRequiredRole() {
        return requiredRole;
    }

    public BigDecimal getThresholdAmount() {
        return thresholdAmount;
    }

    public String getReason() {
        return reason;
    }

    public UserAccount getDecidedBy() {
        return decidedBy;
    }
}
