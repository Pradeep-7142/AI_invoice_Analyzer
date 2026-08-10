package com.invoiceiq.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record VendorSpendResponse(
    UUID vendorId,
    String vendorName,
    String category,
    BigDecimal totalSpend,
    int invoiceCount,
    BigDecimal averageAmount
) {
}
