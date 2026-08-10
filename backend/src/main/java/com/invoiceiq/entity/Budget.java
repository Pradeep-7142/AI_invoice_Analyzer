package com.invoiceiq.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * A recurring monthly spending cap for a vendor category (see
 * {@link Vendor#getCategory()}) — one row per category, applied every
 * month, not a calendar of per-month allocations. Matches how SMB finance
 * teams actually set budgets ("marketing gets 50k/month") without needing
 * a UI to plan every month ahead of time.
 */
@Entity
@Table(name = "budgets")
public class Budget extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(name = "monthly_limit", nullable = false, precision = 14, scale = 2)
    private BigDecimal monthlyLimit;

    @Column(nullable = false, length = 3)
    private String currency;

    protected Budget() {
    }

    public Budget(Organization organization, String category, BigDecimal monthlyLimit, String currency) {
        this.organization = organization;
        this.category = category;
        this.monthlyLimit = monthlyLimit;
        this.currency = currency;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getMonthlyLimit() {
        return monthlyLimit;
    }

    public void setMonthlyLimit(BigDecimal monthlyLimit) {
        this.monthlyLimit = monthlyLimit;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
