package com.invoiceiq.entity;

/**
 * Per-document pipeline status. Only UPLOADED, REJECTED and NEEDS_REVIEW are
 * reachable until Phase 4 wires in OCR/AI extraction (OCR_PROCESSING,
 * EXTRACTING, EXTRACTED, VALIDATING_DATA, PROCESSED, FAILED).
 */
public enum DocumentProcessingStatus {
    UPLOADED,
    VALIDATING,
    REJECTED,
    OCR_PROCESSING,
    EXTRACTING,
    EXTRACTED,
    VALIDATING_DATA,
    NEEDS_REVIEW,
    VERIFIED,
    PROCESSED,
    FAILED
}
