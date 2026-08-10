package com.invoiceiq.validation;

import com.invoiceiq.exception.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidFileException extends ApiException {
    public InvalidFileException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_FILE", message);
    }
}
