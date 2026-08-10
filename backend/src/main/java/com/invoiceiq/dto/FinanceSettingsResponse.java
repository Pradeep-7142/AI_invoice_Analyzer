package com.invoiceiq.dto;

import java.math.BigDecimal;

public record FinanceSettingsResponse(
    BigDecimal managerApprovalThreshold,
    BigDecimal adminApprovalThreshold
) {
}
