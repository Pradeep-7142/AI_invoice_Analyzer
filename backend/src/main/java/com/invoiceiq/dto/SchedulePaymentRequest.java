package com.invoiceiq.dto;

import com.invoiceiq.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SchedulePaymentRequest(
    @NotNull(message = "Amount is required.")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero.")
    BigDecimal amount,

    @NotNull(message = "Scheduled date is required.")
    LocalDate scheduledDate,

    @NotNull(message = "Payment method is required.")
    PaymentMethod method,

    @Size(max = 255)
    String reference,

    String notes
) {
}
