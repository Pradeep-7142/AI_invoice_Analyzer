package com.invoiceiq.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "invoice_line_items")
public class InvoiceLineItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "line_order", nullable = false)
    private int lineOrder;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    protected InvoiceLineItem() {
    }

    public InvoiceLineItem(Invoice invoice, int lineOrder, String description, BigDecimal quantity,
                            BigDecimal unitPrice, BigDecimal taxAmount, BigDecimal discountAmount, BigDecimal totalAmount) {
        this.invoice = invoice;
        this.lineOrder = lineOrder;
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.taxAmount = taxAmount;
        this.discountAmount = discountAmount;
        this.totalAmount = totalAmount;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public int getLineOrder() {
        return lineOrder;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
}
