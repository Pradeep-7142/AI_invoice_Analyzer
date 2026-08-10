package com.invoiceiq.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record BudgetRequest(
    @NotBlank(message = "Category is required.")
    @Size(max = 100)
    String category,

    @NotNull(message = "Monthly limit is required.")
    @DecimalMin(value = "0.01", message = "Monthly limit must be greater than zero.")
    BigDecimal monthlyLimit,

    @Size(min = 3, max = 3)
    String currency
) {
}
