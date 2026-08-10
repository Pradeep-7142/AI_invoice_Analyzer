package com.invoiceiq.dto;

import com.invoiceiq.entity.VendorStatus;
import java.time.Instant;
import java.util.UUID;

public record VendorResponse(
    UUID id,
    String name,
    String email,
    String phone,
    String address,
    String gstin,
    String taxId,
    String category,
    String notes,
    VendorStatus status,
    Instant createdAt,
    Instant updatedAt
) {
}
