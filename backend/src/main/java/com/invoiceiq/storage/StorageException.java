package com.invoiceiq.storage;

import com.invoiceiq.exception.ApiException;
import org.springframework.http.HttpStatus;

public class StorageException extends ApiException {
    public StorageException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", message);
        initCause(cause);
    }
}
