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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "invoices")
public class Invoice extends BaseEntity {

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

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "vendor_name_raw")
    private String vendorNameRaw;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_confidence", columnDefinition = "jsonb")
    private Map<String, Double> fieldConfidence;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("lineOrder ASC")
    private List<InvoiceLineItem> lineItems = new ArrayList<>();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    @OrderBy("createdAt DESC")
    private List<InvoiceDocument> documents = new ArrayList<>();

    public Invoice(UserAccount submittedBy) {
        this.submittedBy = submittedBy;
        this.status = InvoiceStatus.UPLOADED;
    }

    public void replaceLineItems(List<InvoiceLineItem> newLineItems) {
        this.lineItems.clear();
        if (newLineItems != null) {
            this.lineItems.addAll(newLineItems);
        }
    }

    public void addDocument(InvoiceDocument document) {
        this.documents.add(document);
    }
}
