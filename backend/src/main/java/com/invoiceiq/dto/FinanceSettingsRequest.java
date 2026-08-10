package com.invoiceiq.dto;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record FinanceSettingsRequest(
    @DecimalMin(value = "0.00", message = "Threshold cannot be negative.")
    BigDecimal managerApprovalThreshold,

    @DecimalMin(value = "0.00", message = "Threshold cannot be negative.")
    BigDecimal adminApprovalThreshold
) {
}
