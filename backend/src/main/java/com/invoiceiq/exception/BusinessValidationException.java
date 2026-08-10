package com.invoiceiq.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when input is well-formed but violates a domain rule
 * (e.g. invoice total inconsistent with line items, invalid status transition).
 */
public class BusinessValidationException extends ApiException {
    public BusinessValidationException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", message);
    }
}
