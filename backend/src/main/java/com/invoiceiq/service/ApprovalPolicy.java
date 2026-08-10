package com.invoiceiq.service;

import com.invoiceiq.entity.OrgRole;
import com.invoiceiq.entity.Organization;
import java.math.BigDecimal;

/**
 * Pure threshold routing: given an organization's configured approval
 * thresholds and an invoice amount, decides whether a human approval is
 * required before the invoice can be paid, and if so which role is
 * authorized to give it. An ORGANIZATION_ADMIN can always approve
 * anything a FINANCE_MANAGER can (see {@code InvoiceService.approve});
 * this only computes the *minimum* role required.
 */
final class ApprovalPolicy {

    private ApprovalPolicy() {
    }

    /** Returns null when no approval is required (amount below every configured threshold). */
    static OrgRole requiredApproverRole(Organization organization, BigDecimal invoiceAmount) {
        BigDecimal amount = invoiceAmount == null ? BigDecimal.ZERO : invoiceAmount;
        BigDecimal adminThreshold = organization.getAdminApprovalThreshold();
        BigDecimal managerThreshold = organization.getManagerApprovalThreshold();

        if (adminThreshold != null && amount.compareTo(adminThreshold) >= 0) {
            return OrgRole.ORGANIZATION_ADMIN;
        }
        if (managerThreshold != null && amount.compareTo(managerThreshold) >= 0) {
            return OrgRole.FINANCE_MANAGER;
        }
        return null;
    }
}
