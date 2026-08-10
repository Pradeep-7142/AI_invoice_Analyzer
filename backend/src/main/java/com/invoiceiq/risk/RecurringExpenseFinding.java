package com.invoiceiq.risk;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringExpenseFinding(
    String frequency, int occurrences, BigDecimal averageAmount, LocalDate expectedNextDate, String explanation
) {
}
