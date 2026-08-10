package com.invoiceiq.processing;

/** Internal-only: caught by {@link DocumentIntelligenceService} and turned into a rejected document. We never attempt to bypass the password. */
class PasswordProtectedDocumentException extends RuntimeException {
    PasswordProtectedDocumentException(String message) {
        super(message);
    }
}
