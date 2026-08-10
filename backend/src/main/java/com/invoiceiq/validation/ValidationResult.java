package com.invoiceiq.validation;

public record ValidationResult(String rule, ValidationStatus status, String message) {

    public static ValidationResult pass(String rule, String message) {
        return new ValidationResult(rule, ValidationStatus.PASS, message);
    }

    public static ValidationResult warning(String rule, String message) {
        return new ValidationResult(rule, ValidationStatus.WARNING, message);
    }

    public static ValidationResult error(String rule, String message) {
        return new ValidationResult(rule, ValidationStatus.ERROR, message);
    }
}
