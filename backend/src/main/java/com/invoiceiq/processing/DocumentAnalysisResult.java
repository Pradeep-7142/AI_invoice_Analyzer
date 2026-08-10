package com.invoiceiq.processing;

import com.invoiceiq.ai.ExtractedInvoiceFields;
import com.invoiceiq.entity.DocumentProcessingStatus;
import com.invoiceiq.entity.DocumentType;

/**
 * Outcome of running a freshly uploaded document through classification and
 * extraction. When {@code processingStatus} is REJECTED, {@code
 * extractedFields} is null — a rejected document never has its text mined
 * for invoice fields.
 */
public record DocumentAnalysisResult(
    String extractedText,
    Integer pageCount,
    Double ocrConfidence,
    DocumentType documentType,
    DocumentProcessingStatus processingStatus,
    String rejectionReason,
    ExtractedInvoiceFields extractedFields
) {
}
