package com.invoiceiq.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "organizations")
public class Organization extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    /** Invoices at or above this amount need a FINANCE_MANAGER/ORGANIZATION_ADMIN
     * approval before they can be paid; null means no manager-level gate. */
    @Column(name = "manager_approval_threshold", precision = 14, scale = 2)
    private BigDecimal managerApprovalThreshold;

    /** Invoices at or above this amount need an ORGANIZATION_ADMIN approval
     * specifically; null means no admin-level gate. */
    @Column(name = "admin_approval_threshold", precision = 14, scale = 2)
    private BigDecimal adminApprovalThreshold;

    protected Organization() {
    }

    public Organization(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public BigDecimal getManagerApprovalThreshold() {
        return managerApprovalThreshold;
    }

    public void setManagerApprovalThreshold(BigDecimal managerApprovalThreshold) {
        this.managerApprovalThreshold = managerApprovalThreshold;
    }

    public BigDecimal getAdminApprovalThreshold() {
        return adminApprovalThreshold;
    }

    public void setAdminApprovalThreshold(BigDecimal adminApprovalThreshold) {
        this.adminApprovalThreshold = adminApprovalThreshold;
    }
}
