package com.invoiceiq.dto;

import com.invoiceiq.validation.ValidationStatus;

public record ValidationResultDto(String rule, ValidationStatus status, String message) {
}
