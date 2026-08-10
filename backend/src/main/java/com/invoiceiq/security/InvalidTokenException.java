package com.invoiceiq.security;

import com.invoiceiq.exception.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidTokenException extends ApiException {
    public InvalidTokenException(String message) {
        super(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", message);
    }
}
