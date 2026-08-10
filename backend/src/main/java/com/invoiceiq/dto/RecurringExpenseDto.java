package com.invoiceiq.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringExpenseDto(
    String frequency,
    int occurrences,
    BigDecimal averageAmount,
    LocalDate expectedNextDate,
    String explanation
) {
}
