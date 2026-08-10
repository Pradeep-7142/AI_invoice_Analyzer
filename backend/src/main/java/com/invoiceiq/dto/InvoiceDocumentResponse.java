package com.invoiceiq.dto;

import com.invoiceiq.entity.DocumentProcessingStatus;
import com.invoiceiq.entity.DocumentType;
import java.time.Instant;
import java.util.UUID;

public record InvoiceDocumentResponse(
    UUID id,
    String originalFilename,
    String contentType,
    long fileSizeBytes,
    DocumentProcessingStatus processingStatus,
    DocumentType documentType,
    Double ocrConfidence,
    String rejectionReason,
    Instant createdAt
) {
}
