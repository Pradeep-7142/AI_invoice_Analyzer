package com.invoiceiq.processing;

/** Internal-only: caught by {@link DocumentIntelligenceService} and turned into a rejected document, never surfaced as a raw error. */
class CorruptedDocumentException extends RuntimeException {
    CorruptedDocumentException(String message, Throwable cause) {
        super(message, cause);
    }
}
