package com.invoiceiq.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "invoice_documents")
public class InvoiceDocument extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 32)
    private DocumentProcessingStatus processingStatus = DocumentProcessingStatus.UPLOADED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_user_id", nullable = false)
    private UserAccount uploadedBy;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "ocr_confidence", precision = 4, scale = 3)
    private BigDecimal ocrConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", length = 32)
    private DocumentType documentType;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    protected InvoiceDocument() {
    }

    public InvoiceDocument(Invoice invoice, String storageKey, String originalFilename, String contentType,
                            long fileSizeBytes, String checksumSha256, UserAccount uploadedBy) {
        this.invoice = invoice;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSizeBytes = fileSizeBytes;
        this.checksumSha256 = checksumSha256;
        this.uploadedBy = uploadedBy;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public DocumentProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(DocumentProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    public UserAccount getUploadedBy() {
        return uploadedBy;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }

    public BigDecimal getOcrConfidence() {
        return ocrConfidence;
    }

    public void setOcrConfidence(BigDecimal ocrConfidence) {
        this.ocrConfidence = ocrConfidence;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
}
