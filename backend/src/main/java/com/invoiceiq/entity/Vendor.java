package com.invoiceiq.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "vendors")
public class Vendor extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String email;
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String address;

    private String gstin;

    @Column(name = "tax_id")
    private String taxId;

    private String category;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VendorStatus status = VendorStatus.ACTIVE;

    public Vendor(String name) {
        this.name = name;
        this.status = VendorStatus.ACTIVE;
    }
}
