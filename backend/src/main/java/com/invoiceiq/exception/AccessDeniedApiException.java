package com.invoiceiq.exception;

import org.springframework.http.HttpStatus;

public class AccessDeniedApiException extends ApiException {
    public AccessDeniedApiException(String message) {
        super(HttpStatus.FORBIDDEN, "ACCESS_DENIED", message);
    }
}
