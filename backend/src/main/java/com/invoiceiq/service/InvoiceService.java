package com.invoiceiq.service;

import com.invoiceiq.audit.AuditLogService;
import com.invoiceiq.ai.ExtractedInvoiceFields;
import com.invoiceiq.dto.DuplicateWarningDto;
import com.invoiceiq.dto.InvoiceDocumentResponse;
import com.invoiceiq.dto.InvoiceLineItemRequest;
import com.invoiceiq.dto.InvoiceLineItemResponse;
import com.invoiceiq.dto.InvoiceResponse;
import com.invoiceiq.dto.InvoiceSummaryResponse;
import com.invoiceiq.dto.InvoiceUpdateRequest;
import com.invoiceiq.dto.RejectInvoiceRequest;
import com.invoiceiq.dto.UserSummaryDto;
import com.invoiceiq.dto.ValidationResultDto;
import com.invoiceiq.dto.VendorSummaryDto;
import com.invoiceiq.entity.DocumentProcessingStatus;
import com.invoiceiq.entity.Invoice;
import com.invoiceiq.entity.InvoiceDocument;
import com.invoiceiq.entity.InvoiceLineItem;
import com.invoiceiq.entity.InvoiceStatus;
import com.invoiceiq.entity.UserAccount;
import com.invoiceiq.entity.Vendor;
import com.invoiceiq.exception.AccessDeniedApiException;
import com.invoiceiq.exception.BusinessValidationException;
import com.invoiceiq.exception.ResourceNotFoundException;
import com.invoiceiq.export.CsvWriter;
import com.invoiceiq.processing.DocumentAnalysisResult;
import com.invoiceiq.processing.DocumentIntelligenceService;
import com.invoiceiq.repository.InvoiceRepository;
import com.invoiceiq.repository.UserAccountRepository;
import com.invoiceiq.repository.VendorRepository;
import com.invoiceiq.risk.DuplicateDetectionService;
import com.invoiceiq.risk.DuplicateWarning;
import com.invoiceiq.security.CurrentUser;
import com.invoiceiq.storage.StorageService;
import com.invoiceiq.storage.StoredFile;
import com.invoiceiq.validation.FileValidationService;
import com.invoiceiq.validation.InvoiceValidationService;
import com.invoiceiq.validation.ValidationResult;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final VendorRepository vendorRepository;
    private final UserAccountRepository userAccountRepository;
    private final CurrentUser currentUser;
    private final AuditLogService auditLogService;
    private final StorageService storageService;
    private final FileValidationService fileValidationService;
    private final DocumentIntelligenceService documentIntelligenceService;
    private final InvoiceValidationService invoiceValidationService;
    private final DuplicateDetectionService duplicateDetectionService;

    public InvoiceService(
        InvoiceRepository invoiceRepository,
        VendorRepository vendorRepository,
        UserAccountRepository userAccountRepository,
        CurrentUser currentUser,
        AuditLogService auditLogService,
        StorageService storageService,
        FileValidationService fileValidationService,
        DocumentIntelligenceService documentIntelligenceService,
        InvoiceValidationService invoiceValidationService,
        DuplicateDetectionService duplicateDetectionService
    ) {
        this.invoiceRepository = invoiceRepository;
        this.vendorRepository = vendorRepository;
        this.userAccountRepository = userAccountRepository;
        this.currentUser = currentUser;
        this.auditLogService = auditLogService;
        this.storageService = storageService;
        this.fileValidationService = fileValidationService;
        this.documentIntelligenceService = documentIntelligenceService;
        this.invoiceValidationService = invoiceValidationService;
        this.duplicateDetectionService = duplicateDetectionService;
    }

    @Transactional
    public InvoiceResponse upload(String originalFilename, byte[] content) {
        String detectedContentType = fileValidationService.validate(content, originalFilename);
        UserAccount submittedBy = currentUser.entity();

        Invoice invoice = new Invoice(submittedBy);
        invoice.setStatus(InvoiceStatus.NEEDS_REVIEW);
        invoiceRepository.save(invoice);

        StoredFile stored = storageService.store(invoice.getId(), originalFilename, content);

        InvoiceDocument document = new InvoiceDocument(
            invoice,
            stored.storageKey(),
            originalFilename,
            detectedContentType,
            stored.sizeBytes(),
            stored.checksumSha256(),
            submittedBy
        );

        DocumentAnalysisResult analysis = documentIntelligenceService.analyze(content, detectedContentType);
        document.setProcessingStatus(analysis.processingStatus());
        document.setDocumentType(analysis.documentType());
        document.setRejectionReason(analysis.rejectionReason());
        document.setExtractedText(analysis.extractedText());
        if (analysis.ocrConfidence() != null) {
            document.setOcrConfidence(BigDecimal.valueOf(analysis.ocrConfidence()));
        }

        invoice.addDocument(document);

        if (analysis.processingStatus() == DocumentProcessingStatus.PROCESSED && analysis.extractedFields() != null) {
            applyAnalysis(invoice, analysis.extractedFields());
        }

        invoiceRepository.save(invoice);
        auditLogService.record(submittedBy, "invoice.uploaded", "Invoice", invoice.getId().toString(),
            Map.of("filename", originalFilename, "status", invoice.getStatus().name()));

        return toDetailResponse(invoice);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(UUID id) {
        Invoice invoice = findAuthorizedInvoice(id);
        return toDetailResponse(invoice);
    }

    @Transactional(readOnly = true)
    public Page<InvoiceSummaryResponse> list(InvoiceStatus status, String search, Pageable pageable) {
        Page<Invoice> page;
        String term = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        if (currentUser.isAdmin()) {
            if (term != null) {
                page = invoiceRepository.searchInvoices(status, term, pageable);
            } else if (status != null) {
                page = invoiceRepository.findByStatus(status, pageable);
            } else {
                page = invoiceRepository.findAll(pageable);
            }
        } else {
            if (term != null) {
                page = invoiceRepository.searchInvoicesForUser(currentUser.userId(), status, term, pageable);
            } else if (status != null) {
                page = invoiceRepository.findBySubmittedByIdAndStatus(currentUser.userId(), status, pageable);
            } else {
                page = invoiceRepository.findBySubmittedById(currentUser.userId(), pageable);
            }
        }
        return page.map(this::toSummaryResponse);
    }

    @Transactional
    public InvoiceResponse update(UUID id, InvoiceUpdateRequest request) {
        Invoice invoice = findAuthorizedInvoice(id);

        if (invoice.getStatus() == InvoiceStatus.APPROVED || invoice.getStatus() == InvoiceStatus.ARCHIVED) {
            throw new BusinessValidationException("Cannot edit an invoice that is already " + invoice.getStatus());
        }

        if (request.vendorId() != null) {
            Vendor vendor = vendorRepository.findById(request.vendorId())
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found with id: " + request.vendorId()));
            invoice.setVendor(vendor);
        } else {
            invoice.setVendor(null);
        }

        invoice.setInvoiceNumber(request.invoiceNumber());
        invoice.setInvoiceDate(request.invoiceDate());
        invoice.setDueDate(request.dueDate());
        if (request.currency() != null) {
            invoice.setCurrency(request.currency());
        }
        invoice.setSubtotalAmount(request.subtotalAmount());
        invoice.setTaxAmount(request.taxAmount());
        invoice.setDiscountAmount(request.discountAmount());
        invoice.setTotalAmount(request.totalAmount());
        invoice.setNotes(request.notes());

        if (request.lineItems() != null) {
            List<InvoiceLineItem> items = new ArrayList<>();
            int order = 1;
            for (InvoiceLineItemRequest r : request.lineItems()) {
                items.add(new InvoiceLineItem(
                    invoice,
                    order++,
                    r.description(),
                    r.quantity(),
                    r.unitPrice(),
                    r.taxAmount(),
                    r.discountAmount(),
                    r.totalAmount()
                ));
            }
            invoice.replaceLineItems(items);
        }

        invoiceRepository.save(invoice);
        auditLogService.record(currentUser.entity(), "invoice.updated", "Invoice", invoice.getId().toString(), null);

        return toDetailResponse(invoice);
    }

    @Transactional
    public InvoiceResponse verify(UUID id) {
        Invoice invoice = findAuthorizedInvoice(id);

        List<ValidationResult> validationResults = invoiceValidationService.validate(invoice);
        if (invoiceValidationService.hasBlockingErrors(validationResults)) {
            List<String> errors = validationResults.stream()
                .filter(r -> r.status() == com.invoiceiq.validation.ValidationStatus.ERROR)
                .map(ValidationResult::message)
                .toList();
            throw new BusinessValidationException("Cannot verify invoice with blocking validation errors: " + String.join("; ", errors));
        }

        invoice.setStatus(InvoiceStatus.VERIFIED);
        invoice.setRejectionReason(null);
        invoiceRepository.save(invoice);

        auditLogService.record(currentUser.entity(), "invoice.verified", "Invoice", invoice.getId().toString(), null);
        return toDetailResponse(invoice);
    }

    @Transactional
    public InvoiceResponse approve(UUID id) {
        if (!currentUser.isAdmin()) {
            throw new AccessDeniedApiException("Only administrators can approve invoices.");
        }

        Invoice invoice = invoiceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + id));

        if (invoice.getStatus() != InvoiceStatus.VERIFIED && invoice.getStatus() != InvoiceStatus.NEEDS_REVIEW) {
            throw new BusinessValidationException("Invoice must be verified before approval.");
        }

        invoice.setStatus(InvoiceStatus.APPROVED);
        invoice.setRejectionReason(null);
        invoiceRepository.save(invoice);

        auditLogService.record(currentUser.entity(), "invoice.approved", "Invoice", invoice.getId().toString(), null);
        return toDetailResponse(invoice);
    }

    @Transactional
    public InvoiceResponse reject(UUID id, RejectInvoiceRequest request) {
        if (!currentUser.isAdmin()) {
            throw new AccessDeniedApiException("Only administrators can reject invoices.");
        }

        Invoice invoice = invoiceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + id));

        invoice.setStatus(InvoiceStatus.REJECTED);
        invoice.setRejectionReason(request.reason());
        invoiceRepository.save(invoice);

        auditLogService.record(currentUser.entity(), "invoice.rejected", "Invoice", invoice.getId().toString(),
            Map.of("reason", request.reason()));
        return toDetailResponse(invoice);
    }

    @Transactional
    public InvoiceResponse archive(UUID id) {
        Invoice invoice = findAuthorizedInvoice(id);
        invoice.setStatus(InvoiceStatus.ARCHIVED);
        invoiceRepository.save(invoice);

        auditLogService.record(currentUser.entity(), "invoice.archived", "Invoice", invoice.getId().toString(), null);
        return toDetailResponse(invoice);
    }

    @Transactional
    public void delete(UUID id) {
        if (!currentUser.isAdmin()) {
            throw new AccessDeniedApiException("Only administrators can delete invoices.");
        }

        Invoice invoice = invoiceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + id));

        invoiceRepository.delete(invoice);
        auditLogService.record(currentUser.entity(), "invoice.deleted", "Invoice", id.toString(), null);
    }

    public InputStream downloadDocument(UUID invoiceId, UUID documentId) {
        Invoice invoice = findAuthorizedInvoice(invoiceId);
        InvoiceDocument document = invoice.getDocuments().stream()
            .filter(d -> d.getId().equals(documentId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        return storageService.retrieve(document.getStorageKey());
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(InvoiceStatus status) {
        List<Invoice> invoices = currentUser.isAdmin()
            ? (status != null ? invoiceRepository.findByStatus(status) : invoiceRepository.findAll())
            : (status != null
                ? invoiceRepository.findBySubmittedByIdAndStatus(currentUser.userId(), status)
                : invoiceRepository.findBySubmittedById(currentUser.userId()));

        List<InvoiceSummaryResponse> summaries = invoices.stream().map(this::toSummaryResponse).toList();
        return CsvWriter.writeInvoices(summaries);
    }

    private Invoice findAuthorizedInvoice(UUID id) {
        Invoice invoice = invoiceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + id));

        if (!currentUser.isAdmin() && !invoice.getSubmittedBy().getId().equals(currentUser.userId())) {
            throw new AccessDeniedApiException("You do not have permission to access this invoice.");
        }
        return invoice;
    }

    private void applyAnalysis(Invoice invoice, ExtractedInvoiceFields fields) {
        if (fields.invoiceNumber() != null) {
            invoice.setInvoiceNumber(fields.invoiceNumber());
        }
        if (fields.invoiceDate() != null) {
            invoice.setInvoiceDate(fields.invoiceDate());
        }
        if (fields.dueDate() != null) {
            invoice.setDueDate(fields.dueDate());
        }
        if (fields.subtotalAmount() != null) {
            invoice.setSubtotalAmount(fields.subtotalAmount());
        }
        if (fields.taxAmount() != null) {
            invoice.setTaxAmount(fields.taxAmount());
        }
        if (fields.totalAmount() != null) {
            invoice.setTotalAmount(fields.totalAmount());
        }
        if (fields.confidences() != null) {
            invoice.setFieldConfidence(fields.confidences());
        }

        if (fields.vendorNameRaw() != null && !fields.vendorNameRaw().isBlank()) {
            invoice.setVendorNameRaw(fields.vendorNameRaw());
            Optional<Vendor> matchedVendor = vendorRepository.findFirstByNameIgnoreCase(fields.vendorNameRaw());
            if (matchedVendor.isPresent()) {
                invoice.setVendor(matchedVendor.get());
            } else {
                Vendor newVendor = new Vendor(fields.vendorNameRaw());
                if (fields.gstin() != null) {
                    newVendor.setGstin(fields.gstin());
                }
                vendorRepository.save(newVendor);
                invoice.setVendor(newVendor);
            }
        }
    }

    public InvoiceResponse toDetailResponse(Invoice invoice) {
        List<ValidationResult> validationResults = invoiceValidationService.validate(invoice);
        List<ValidationResultDto> validationDtos = validationResults.stream()
            .map(r -> new ValidationResultDto(r.rule(), r.status(), r.message()))
            .toList();

        List<DuplicateWarningDto> duplicateDtos = List.of();
        if (invoice.getVendor() != null && invoice.getId() != null) {
            List<Invoice> history = invoiceRepository.findByVendorIdAndIdNotAndStatusNot(
                invoice.getVendor().getId(), invoice.getId(), InvoiceStatus.ARCHIVED);
            List<DuplicateWarning> warnings = duplicateDetectionService.findDuplicates(invoice, history);
            duplicateDtos = warnings.stream()
                .map(w -> new DuplicateWarningDto(w.invoiceId(), w.invoiceNumber(), w.probability(), w.reason()))
                .toList();
        }

        VendorSummaryDto vendorDto = invoice.getVendor() != null
            ? new VendorSummaryDto(invoice.getVendor().getId(), invoice.getVendor().getName())
            : null;

        UserSummaryDto submitterDto = invoice.getSubmittedBy() != null
            ? new UserSummaryDto(
                invoice.getSubmittedBy().getId(),
                invoice.getSubmittedBy().getEmail(),
                invoice.getSubmittedBy().getFullName(),
                invoice.getSubmittedBy().getRole(),
                invoice.getSubmittedBy().getStatus()
            )
            : null;

        List<InvoiceLineItemResponse> lineItemDtos = invoice.getLineItems().stream()
            .map(item -> new InvoiceLineItemResponse(
                item.getId(),
                item.getLineOrder(),
                item.getDescription(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getTaxAmount(),
                item.getDiscountAmount(),
                item.getTotalAmount()
            ))
            .toList();

        List<InvoiceDocumentResponse> documentDtos = invoice.getDocuments().stream()
            .map(doc -> new InvoiceDocumentResponse(
                doc.getId(),
                doc.getOriginalFilename(),
                doc.getContentType(),
                doc.getFileSizeBytes(),
                doc.getProcessingStatus(),
                doc.getDocumentType(),
                doc.getOcrConfidence() != null ? doc.getOcrConfidence().doubleValue() : null,
                doc.getRejectionReason(),
                doc.getCreatedAt()
            ))
            .toList();

        return new InvoiceResponse(
            invoice.getId(),
            vendorDto,
            invoice.getVendorNameRaw(),
            invoice.getInvoiceNumber(),
            invoice.getInvoiceDate(),
            invoice.getDueDate(),
            invoice.getCurrency(),
            invoice.getSubtotalAmount(),
            invoice.getTaxAmount(),
            invoice.getDiscountAmount(),
            invoice.getTotalAmount(),
            invoice.getStatus(),
            invoice.getNotes(),
            invoice.getRejectionReason(),
            invoice.getFieldConfidence(),
            validationDtos,
            duplicateDtos,
            submitterDto,
            lineItemDtos,
            documentDtos,
            invoice.getCreatedAt(),
            invoice.getUpdatedAt()
        );
    }

    public InvoiceSummaryResponse toSummaryResponse(Invoice invoice) {
        VendorSummaryDto vendorDto = invoice.getVendor() != null
            ? new VendorSummaryDto(invoice.getVendor().getId(), invoice.getVendor().getName())
            : null;

        return new InvoiceSummaryResponse(
            invoice.getId(),
            vendorDto,
            invoice.getInvoiceNumber(),
            invoice.getInvoiceDate(),
            invoice.getDueDate(),
            invoice.getCurrency(),
            invoice.getTotalAmount(),
            invoice.getStatus(),
            invoice.getSubmittedBy() != null ? invoice.getSubmittedBy().getFullName() : "—",
            invoice.getCreatedAt()
        );
    }
}
