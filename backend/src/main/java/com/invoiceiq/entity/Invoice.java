package com.invoiceiq.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "invoices")
public class Invoice extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by_user_id", nullable = false)
    private UserAccount submittedBy;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "subtotal_amount", precision = 14, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(name = "tax_amount", precision = 14, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "discount_amount", precision = 14, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "total_amount", precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InvoiceStatus status = InvoiceStatus.UPLOADED;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Reason given the last time this invoice was disputed; cleared when the dispute is resolved. */
    @Column(name = "dispute_reason", columnDefinition = "TEXT")
    private String disputeReason;

    /** Vendor name as read off the document, kept even when it doesn't
     * resolve to an existing {@link Vendor} record, so the reviewer isn't
     * starting from nothing. */
    @Column(name = "vendor_name_raw")
    private String vendorNameRaw;

    /** Per-field AI extraction confidence (0.0–1.0), e.g. {"invoiceNumber": 0.92}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_confidence", columnDefinition = "jsonb")
    private Map<String, Double> fieldConfidence;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("lineOrder ASC")
    private List<InvoiceLineItem> lineItems = new ArrayList<>();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    @OrderBy("createdAt DESC")
    private List<InvoiceDocument> documents = new ArrayList<>();

    protected Invoice() {
    }

    public Invoice(Organization organization, UserAccount submittedBy) {
        this.organization = organization;
        this.submittedBy = submittedBy;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Vendor getVendor() {
        return vendor;
    }

    public void setVendor(Vendor vendor) {
        this.vendor = vendor;
    }

    public UserAccount getSubmittedBy() {
        return submittedBy;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public void setInvoiceDate(LocalDate invoiceDate) {
        this.invoiceDate = invoiceDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getSubtotalAmount() {
        return subtotalAmount;
    }

    public void setSubtotalAmount(BigDecimal subtotalAmount) {
        this.subtotalAmount = subtotalAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(InvoiceStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getDisputeReason() {
        return disputeReason;
    }

    public void setDisputeReason(String disputeReason) {
        this.disputeReason = disputeReason;
    }

    public String getVendorNameRaw() {
        return vendorNameRaw;
    }

    public void setVendorNameRaw(String vendorNameRaw) {
        this.vendorNameRaw = vendorNameRaw;
    }

    public Map<String, Double> getFieldConfidence() {
        return fieldConfidence;
    }

    public void setFieldConfidence(Map<String, Double> fieldConfidence) {
        this.fieldConfidence = fieldConfidence;
    }

    public List<InvoiceLineItem> getLineItems() {
        return lineItems;
    }

    public void replaceLineItems(List<InvoiceLineItem> newLineItems) {
        this.lineItems.clear();
        this.lineItems.addAll(newLineItems);
    }

    public List<InvoiceDocument> getDocuments() {
        return documents;
    }

    public void addDocument(InvoiceDocument document) {
        this.documents.add(document);
    }
}
