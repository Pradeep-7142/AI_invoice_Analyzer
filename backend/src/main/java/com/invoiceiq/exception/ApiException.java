package com.invoiceiq.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for all application-thrown errors that should be surfaced to
 * clients with a specific HTTP status and a machine-readable error code
 * (used by the frontend to branch on error type, not just show text).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
