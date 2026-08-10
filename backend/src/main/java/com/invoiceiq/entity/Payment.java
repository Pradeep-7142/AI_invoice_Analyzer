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
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status = PaymentStatus.SCHEDULED;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "completed_at")
    private Instant completedAt;

    private String reference;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by_user_id", nullable = false)
    private UserAccount recordedBy;

    protected Payment() {
    }

    public Payment(
        Organization organization, Invoice invoice, BigDecimal amount, String currency,
        PaymentMethod method, LocalDate scheduledDate, String reference, String notes, UserAccount recordedBy
    ) {
        this.organization = organization;
        this.invoice = invoice;
        this.amount = amount;
        this.currency = currency;
        this.method = method;
        this.scheduledDate = scheduledDate;
        this.reference = reference;
        this.notes = notes;
        this.recordedBy = recordedBy;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getReference() {
        return reference;
    }

    public String getNotes() {
        return notes;
    }

    public UserAccount getRecordedBy() {
        return recordedBy;
    }
}
