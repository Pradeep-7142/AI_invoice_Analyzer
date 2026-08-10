package com.invoiceiq.service;

import com.invoiceiq.audit.AuditLogService;
import com.invoiceiq.dto.AnomalyDto;
import com.invoiceiq.dto.ApprovalDecisionDto;
import com.invoiceiq.dto.DisputeInvoiceRequest;
import com.invoiceiq.dto.DuplicateWarningDto;
import com.invoiceiq.dto.InvoiceDocumentResponse;
import com.invoiceiq.dto.InvoiceLineItemRequest;
import com.invoiceiq.dto.InvoiceLineItemResponse;
import com.invoiceiq.dto.InvoiceResponse;
import com.invoiceiq.dto.InvoiceSummaryResponse;
import com.invoiceiq.dto.InvoiceUpdateRequest;
import com.invoiceiq.dto.PaymentDto;
import com.invoiceiq.dto.RecurringExpenseDto;
import com.invoiceiq.dto.RejectInvoiceRequest;
import com.invoiceiq.dto.SchedulePaymentRequest;
import com.invoiceiq.dto.UserSummaryDto;
import com.invoiceiq.dto.ValidationResultDto;
import com.invoiceiq.dto.VendorSummaryDto;
import com.invoiceiq.entity.ApprovalDecisionType;
import com.invoiceiq.entity.Invoice;
import com.invoiceiq.entity.InvoiceApproval;
import com.invoiceiq.entity.InvoiceDocument;
import com.invoiceiq.entity.InvoiceLineItem;
import com.invoiceiq.entity.InvoiceStatus;
import com.invoiceiq.entity.OrgRole;
import com.invoiceiq.entity.Organization;
import com.invoiceiq.entity.Payment;
import com.invoiceiq.entity.PaymentStatus;
import com.invoiceiq.entity.UserAccount;
import com.invoiceiq.entity.Vendor;
import com.invoiceiq.entity.VendorStatus;
import com.invoiceiq.exception.BusinessValidationException;
import com.invoiceiq.exception.ResourceNotFoundException;
import com.invoiceiq.export.CsvWriter;
import com.invoiceiq.ai.ExtractedInvoiceFields;
import com.invoiceiq.processing.DocumentAnalysisResult;
import com.invoiceiq.processing.DocumentIntelligenceService;
import com.invoiceiq.repository.InvoiceApprovalRepository;
import com.invoiceiq.repository.InvoiceRepository;
import com.invoiceiq.repository.OrganizationRepository;
import com.invoiceiq.repository.UserAccountRepository;
import com.invoiceiq.repository.VendorRepository;
import com.invoiceiq.risk.AnomalyDetectionService;
import com.invoiceiq.risk.AnomalyFinding;
import com.invoiceiq.risk.DuplicateDetectionService;
import com.invoiceiq.risk.DuplicateWarning;
import com.invoiceiq.risk.RecurringExpenseDetectionService;
import com.invoiceiq.risk.RecurringExpenseFinding;
import com.invoiceiq.risk.RiskScore;
import com.invoiceiq.risk.RiskScoringService;
import com.invoiceiq.security.CurrentUser;
import com.invoiceiq.storage.StorageService;
import com.invoiceiq.storage.StoredFile;
import com.invoiceiq.validation.FileValidationService;
import com.invoiceiq.validation.InvoiceValidationService;
import com.invoiceiq.validation.ValidationResult;
import com.invoiceiq.validation.ValidationStatus;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

    /** Statuses a dispute can be raised from — anything post-verification that isn't already archived/disputed. */
    private static final Set<InvoiceStatus> DISPUTABLE_STATUSES = Set.of(
        InvoiceStatus.VERIFIED, InvoiceStatus.PENDING_APPROVAL, InvoiceStatus.APPROVED,
        InvoiceStatus.PAYMENT_SCHEDULED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.OVERDUE, InvoiceStatus.PAID);

    private final InvoiceRepository invoiceRepository;
    private final VendorRepository vendorRepository;
    private final OrganizationRepository organizationRepository;
    private final UserAccountRepository userAccountRepository;
    private final InvoiceApprovalRepository invoiceApprovalRepository;
    private final CurrentUser currentUser;
    private final AuditLogService auditLogService;
    private final StorageService storageService;
    private final FileValidationService fileValidationService;
    private final DocumentIntelligenceService documentIntelligenceService;
    private final InvoiceValidationService invoiceValidationService;
    private final DuplicateDetectionService duplicateDetectionService;
    private final AnomalyDetectionService anomalyDetectionService;
    private final RecurringExpenseDetectionService recurringExpenseDetectionService;
    private final RiskScoringService riskScoringService;
    private final PaymentService paymentService;

    public InvoiceService(
        InvoiceRepository invoiceRepository,
        VendorRepository vendorRepository,
        OrganizationRepository organizationRepository,
        UserAccountRepository userAccountRepository,
        InvoiceApprovalRepository invoiceApprovalRepository,
        CurrentUser currentUser,
        AuditLogService auditLogService,
        StorageService storageService,
        FileValidationService fileValidationService,
        DocumentIntelligenceService documentIntelligenceService,
        InvoiceValidationService invoiceValidationService,
        DuplicateDetectionService duplicateDetectionService,
        AnomalyDetectionService anomalyDetectionService,
        RecurringExpenseDetectionService recurringExpenseDetectionService,
        RiskScoringService riskScoringService,
        PaymentService paymentService
    ) {
        this.invoiceRepository = invoiceRepository;
        this.vendorRepository = vendorRepository;
        this.organizationRepository = organizationRepository;
        this.userAccountRepository = userAccountRepository;
        this.invoiceApprovalRepository = invoiceApprovalRepository;
        this.currentUser = currentUser;
        this.auditLogService = auditLogService;
        this.storageService = storageService;
        this.fileValidationService = fileValidationService;
        this.documentIntelligenceService = documentIntelligenceService;
        this.invoiceValidationService = invoiceValidationService;
        this.duplicateDetectionService = duplicateDetectionService;
        this.anomalyDetectionService = anomalyDetectionService;
        this.recurringExpenseDetectionService = recurringExpenseDetectionService;
        this.riskScoringService = riskScoringService;
        this.paymentService = paymentService;
    }

    @Transactional
    public InvoiceResponse upload(String originalFilename, byte[] content) {
        String detectedContentType = fileValidationService.validate(content, originalFilename);

        UUID organizationId = currentUser.organizationId();
        Organization organization = organizationRepository.getReferenceById(organizationId);
        UserAccount submittedBy = userAccountRepository.getReferenceById(currentUser.userId());

        Invoice invoice = new Invoice(organization, submittedBy);
        // Every upload needs a human to confirm the data before it's
        // trusted, whether or not extraction found anything usable — so
        // NEEDS_REVIEW is the honest state regardless of pipeline outcome.
        invoice.setStatus(InvoiceStatus.NEEDS_REVIEW);
        invoiceRepository.save(invoice);

        StoredFile stored = storageService.store(organizationId, invoice.getId(), originalFilename, content);
        InvoiceDocument document = new InvoiceDocument(
            invoice, stored.storageKey(), sanitizeFilenameForDisplay(originalFilename),
            detectedContentType, stored.sizeBytes(), stored.checksumSha256(), submittedBy);
        // invoice is still managed in this transaction's persistence context;
        // cascade=PERSIST on documents inserts this new row at flush time.
        // Calling save() again here would trigger merge() semantics instead
        // (isNew() is false once the invoice has an id), and merge does not
        // apply PERSIST cascade — the child's invoice_id would come back null.
        invoice.addDocument(document);

        DocumentAnalysisResult analysis = documentIntelligenceService.analyze(content, detectedContentType);
        applyAnalysis(invoice, document, analysis, organizationId);

        auditLogService.record(organization, submittedBy, "invoice.uploaded", "Invoice", invoice.getId().toString(),
            Map.of("filename", document.getOriginalFilename(), "documentStatus", document.getProcessingStatus().name()));

        return toDetailResponse(invoice);
    }

    private void applyAnalysis(Invoice invoice, InvoiceDocument document, DocumentAnalysisResult analysis, UUID organizationId) {
        document.setProcessingStatus(analysis.processingStatus());
        document.setDocumentType(analysis.documentType());
        document.setRejectionReason(analysis.rejectionReason());
        document.setExtractedText(truncate(analysis.extractedText(), 50_000));
        document.setOcrConfidence(analysis.ocrConfidence() == null
            ? null : BigDecimal.valueOf(analysis.ocrConfidence()).setScale(3, RoundingMode.HALF_UP));

        ExtractedInvoiceFields fields = analysis.extractedFields();
        if (fields == null) {
            return;
        }

        invoice.setInvoiceNumber(fields.invoiceNumber());
        invoice.setInvoiceDate(fields.invoiceDate());
        invoice.setDueDate(fields.dueDate());
        invoice.setSubtotalAmount(fields.subtotalAmount());
        invoice.setTaxAmount(fields.taxAmount());
        invoice.setTotalAmount(fields.totalAmount());
        invoice.setVendorNameRaw(fields.vendorNameRaw());
        invoice.setFieldConfidence(fields.confidences());

        if (fields.vendorNameRaw() != null) {
            vendorRepository.findFirstByOrganizationIdAndStatusAndNameIgnoreCase(
                    organizationId, VendorStatus.ACTIVE, fields.vendorNameRaw())
                .ifPresent(invoice::setVendor);
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    @Transactional(readOnly = true)
    public Page<InvoiceSummaryResponse> list(InvoiceStatus statusFilter, Pageable pageable) {
        UUID organizationId = currentUser.organizationId();
        Page<Invoice> page;

        if (currentUser.role() == OrgRole.EMPLOYEE) {
            UUID userId = currentUser.userId();
            page = (statusFilter == null)
                ? invoiceRepository.findByOrganizationIdAndSubmittedById(organizationId, userId, pageable)
                : invoiceRepository.findByOrganizationIdAndSubmittedByIdAndStatus(organizationId, userId, statusFilter, pageable);
        } else {
            page = (statusFilter == null)
                ? invoiceRepository.findByOrganizationId(organizationId, pageable)
                : invoiceRepository.findByOrganizationIdAndStatus(organizationId, statusFilter, pageable);
        }

        return page.map(this::toSummaryResponse);
    }

    /** Same visibility rule as {@link #list}, just unpaged — every matching row goes into the file at once. */
    @Transactional(readOnly = true)
    public String exportCsv(InvoiceStatus statusFilter) {
        UUID organizationId = currentUser.organizationId();
        List<Invoice> invoices;

        if (currentUser.role() == OrgRole.EMPLOYEE) {
            UUID userId = currentUser.userId();
            invoices = (statusFilter == null)
                ? invoiceRepository.findByOrganizationIdAndSubmittedById(organizationId, userId)
                : invoiceRepository.findByOrganizationIdAndSubmittedByIdAndStatus(organizationId, userId, statusFilter);
        } else {
            invoices = (statusFilter == null)
                ? invoiceRepository.findByOrganizationId(organizationId)
                : invoiceRepository.findByOrganizationIdAndStatus(organizationId, statusFilter);
        }

        StringBuilder csv = new StringBuilder(CsvWriter.row(
            "Invoice Number", "Vendor", "Invoice Date", "Due Date", "Currency", "Total Amount", "Status", "Submitted By", "Created At"));
        for (Invoice invoice : invoices) {
            csv.append(CsvWriter.row(
                invoice.getInvoiceNumber(),
                invoice.getVendor() != null ? invoice.getVendor().getName() : invoice.getVendorNameRaw(),
                invoice.getInvoiceDate(),
                invoice.getDueDate(),
                invoice.getCurrency(),
                invoice.getTotalAmount(),
                invoice.getStatus(),
                invoice.getSubmittedBy().getFullName(),
                invoice.getCreatedAt()));
        }
        return csv.toString();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(UUID invoiceId) {
        return toDetailResponse(findVisible(invoiceId));
    }

    @Transactional(readOnly = true)
    public DownloadableDocument downloadLatestDocument(UUID invoiceId) {
        Invoice invoice = findVisible(invoiceId);
        List<InvoiceDocument> documents = invoice.getDocuments();
        if (documents.isEmpty()) {
            throw new ResourceNotFoundException("This invoice has no document attached.");
        }
        InvoiceDocument latest = documents.get(0);
        InputStream stream = storageService.retrieve(latest.getStorageKey());
        return new DownloadableDocument(latest.getOriginalFilename(), latest.getContentType(), stream);
    }

    @Transactional
    public InvoiceResponse update(UUID invoiceId, InvoiceUpdateRequest request) {
        Invoice invoice = findOwned(invoiceId);

        if (invoice.getStatus() == InvoiceStatus.ARCHIVED) {
            throw new BusinessValidationException("Cannot edit an archived invoice.");
        }

        if (request.vendorId() != null) {
            Vendor vendor = vendorRepository.findByIdAndOrganizationId(request.vendorId(), currentUser.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found."));
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

        List<InvoiceLineItem> newLineItems = new ArrayList<>();
        List<InvoiceLineItemRequest> requestedLineItems = request.lineItems() == null ? List.of() : request.lineItems();
        for (int i = 0; i < requestedLineItems.size(); i++) {
            InvoiceLineItemRequest item = requestedLineItems.get(i);
            newLineItems.add(new InvoiceLineItem(
                invoice, i, item.description(), item.quantity(), item.unitPrice(),
                item.taxAmount() == null ? BigDecimal.ZERO : item.taxAmount(),
                item.discountAmount() == null ? BigDecimal.ZERO : item.discountAmount(),
                item.totalAmount()));
        }
        invoice.replaceLineItems(newLineItems);

        if (invoice.getStatus() == InvoiceStatus.VERIFIED) {
            InvoiceLifecycle.assertTransitionAllowed(InvoiceStatus.VERIFIED, InvoiceStatus.NEEDS_REVIEW);
            invoice.setStatus(InvoiceStatus.NEEDS_REVIEW);
        }

        auditLogService.record(invoice.getOrganization(), currentUserAccount(), "invoice.updated", "Invoice",
            invoice.getId().toString(), null);

        return toDetailResponse(invoice);
    }

    @Transactional
    public InvoiceResponse verify(UUID invoiceId) {
        Invoice invoice = findOwned(invoiceId);
        InvoiceLifecycle.assertTransitionAllowed(invoice.getStatus(), InvoiceStatus.VERIFIED);

        List<ValidationResult> validationResults = invoiceValidationService.validate(invoice);
        if (invoiceValidationService.hasBlockingErrors(validationResults)) {
            String errors = validationResults.stream()
                .filter(r -> r.status() == ValidationStatus.ERROR)
                .map(ValidationResult::message)
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
            throw new BusinessValidationException("Cannot verify this invoice — " + errors);
        }

        invoice.setStatus(InvoiceStatus.VERIFIED);

        auditLogService.record(invoice.getOrganization(), currentUserAccount(), "invoice.verified", "Invoice",
            invoice.getId().toString(), null);

        return toDetailResponse(invoice);
    }

    @Transactional
    public InvoiceResponse archive(UUID invoiceId) {
        Invoice invoice = findOwned(invoiceId);
        InvoiceLifecycle.assertTransitionAllowed(invoice.getStatus(), InvoiceStatus.ARCHIVED);
        invoice.setStatus(InvoiceStatus.ARCHIVED);

        auditLogService.record(invoice.getOrganization(), currentUserAccount(), "invoice.archived", "Invoice",
            invoice.getId().toString(), null);

        return toDetailResponse(invoice);
    }

    /** Routes a verified invoice to PENDING_APPROVAL, or straight to APPROVED when it's below every threshold. */
    @Transactional
    public InvoiceResponse submitForApproval(UUID invoiceId) {
        Invoice invoice = findOwned(invoiceId);
        OrgRole requiredRole = ApprovalPolicy.requiredApproverRole(invoice.getOrganization(), invoice.getTotalAmount());

        if (requiredRole == null) {
            InvoiceLifecycle.assertTransitionAllowed(invoice.getStatus(), InvoiceStatus.APPROVED);
            invoice.setStatus(InvoiceStatus.APPROVED);
            auditLogService.record(invoice.getOrganization(), currentUserAccount(), "invoice.auto_approved", "Invoice",
                invoice.getId().toString(), Map.of("reason", "Below configured approval thresholds"));
        } else {
            InvoiceLifecycle.assertTransitionAllowed(invoice.getStatus(), InvoiceStatus.PENDING_APPROVAL);
            invoice.setStatus(InvoiceStatus.PENDING_APPROVAL);
            auditLogService.record(invoice.getOrganization(), currentUserAccount(), "invoice.submitted_for_approval",
                "Invoice", invoice.getId().toString(), Map.of("requiredRole", requiredRole.name()));
        }

        return toDetailResponse(invoice);
    }

    @Transactional
    public InvoiceResponse approve(UUID invoiceId) {
        Invoice invoice = findOwned(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.PENDING_APPROVAL) {
            throw new BusinessValidationException("Only an invoice pending approval can be approved.");
        }
        assertCanDecide(invoice);
        InvoiceLifecycle.assertTransitionAllowed(invoice.getStatus(), InvoiceStatus.APPROVED);

        OrgRole requiredRole = ApprovalPolicy.requiredApproverRole(invoice.getOrganization(), invoice.getTotalAmount());
        recordApprovalDecision(invoice, ApprovalDecisionType.APPROVED, requiredRole, null);
        invoice.setStatus(InvoiceStatus.APPROVED);

        auditLogService.record(invoice.getOrganization(), currentUserAccount(), "invoice.approved", "Invoice",
            invoice.getId().toString(), null);

        return toDetailResponse(invoice);
    }

    @Transactional
    public InvoiceResponse reject(UUID invoiceId, RejectInvoiceRequest request) {
        Invoice invoice = findOwned(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.PENDING_APPROVAL) {
            throw new BusinessValidationException("Only an invoice pending approval can be rejected.");
        }
        assertCanDecide(invoice);
        InvoiceLifecycle.assertTransitionAllowed(invoice.getStatus(), InvoiceStatus.NEEDS_REVIEW);

        OrgRole requiredRole = ApprovalPolicy.requiredApproverRole(invoice.getOrganization(), invoice.getTotalAmount());
        recordApprovalDecision(invoice, ApprovalDecisionType.REJECTED, requiredRole, request.reason());
        invoice.setStatus(InvoiceStatus.NEEDS_REVIEW);

        auditLogService.record(invoice.getOrganization(), currentUserAccount(), "invoice.rejected", "Invoice",
            invoice.getId().toString(), Map.of("reason", request.reason()));

        return toDetailResponse(invoice);
    }

    /** Separation of duties: nobody approves or rejects their own submission, and the decider must hold at least the required role. */
    private void assertCanDecide(Invoice invoice) {
        if (invoice.getSubmittedBy().getId().equals(currentUser.userId())) {
            throw new BusinessValidationException("You cannot approve or reject an invoice you submitted yourself.");
        }
        if (currentUser.role() != OrgRole.ORGANIZATION_ADMIN && currentUser.role() != OrgRole.FINANCE_MANAGER) {
            throw new BusinessValidationException("Only a finance manager or organization admin can decide on this invoice.");
        }
        OrgRole requiredRole = ApprovalPolicy.requiredApproverRole(invoice.getOrganization(), invoice.getTotalAmount());
        if (requiredRole == OrgRole.ORGANIZATION_ADMIN && currentUser.role() != OrgRole.ORGANIZATION_ADMIN) {
            throw new BusinessValidationException("This invoice's amount requires an organization admin's decision.");
        }
    }

    private void recordApprovalDecision(Invoice invoice, ApprovalDecisionType decision, OrgRole requiredRole, String reason) {
        OrgRole recordedRole = requiredRole == null ? OrgRole.FINANCE_MANAGER : requiredRole;
        BigDecimal thresholdAmount = recordedRole == OrgRole.ORGANIZATION_ADMIN
            ? invoice.getOrganization().getAdminApprovalThreshold()
            : invoice.getOrganization().getManagerApprovalThreshold();
        invoiceApprovalRepository.save(new InvoiceApproval(
            invoice, invoice.getOrganization(), decision, recordedRole, thresholdAmount, reason, currentUserAccount()));
    }

    @Transactional
    public InvoiceResponse dispute(UUID invoiceId, DisputeInvoiceRequest request) {
        Invoice invoice = findOwned(invoiceId);
        if (!DISPUTABLE_STATUSES.contains(invoice.getStatus())) {
            throw new BusinessValidationException("Cannot dispute an invoice with status " + invoice.getStatus() + ".");
        }
        InvoiceLifecycle.assertTransitionAllowed(invoice.getStatus(), InvoiceStatus.DISPUTED);
        invoice.setDisputeReason(request.reason());
        invoice.setStatus(InvoiceStatus.DISPUTED);

        auditLogService.record(invoice.getOrganization(), currentUserAccount(), "invoice.disputed", "Invoice",
            invoice.getId().toString(), Map.of("reason", request.reason()));

        return toDetailResponse(invoice);
    }

    /** Sends a disputed invoice back to NEEDS_REVIEW — the same honest landing spot an edit uses, since something needs reassessing. */
    @Transactional
    public InvoiceResponse resolveDispute(UUID invoiceId) {
        Invoice invoice = findOwned(invoiceId);
        InvoiceLifecycle.assertTransitionAllowed(invoice.getStatus(), InvoiceStatus.NEEDS_REVIEW);
        invoice.setStatus(InvoiceStatus.NEEDS_REVIEW);
        invoice.setDisputeReason(null);

        auditLogService.record(invoice.getOrganization(), currentUserAccount(), "invoice.dispute_resolved", "Invoice",
            invoice.getId().toString(), null);

        return toDetailResponse(invoice);
    }

    @Transactional
    public InvoiceResponse schedulePayment(UUID invoiceId, SchedulePaymentRequest request) {
        Invoice invoice = findOwned(invoiceId);
        paymentService.schedule(invoice, request, currentUserAccount());
        return toDetailResponse(invoice);
    }

    @Transactional
    public InvoiceResponse completePayment(UUID invoiceId, UUID paymentId) {
        Invoice invoice = findOwned(invoiceId);
        paymentService.complete(invoice, paymentId, currentUserAccount());
        return toDetailResponse(invoice);
    }

    @Transactional
    public InvoiceResponse cancelPayment(UUID invoiceId, UUID paymentId) {
        Invoice invoice = findOwned(invoiceId);
        paymentService.cancel(invoice, paymentId, currentUserAccount());
        return toDetailResponse(invoice);
    }

    private Invoice findOwned(UUID invoiceId) {
        return invoiceRepository.findByIdAndOrganizationId(invoiceId, currentUser.organizationId())
            .orElseThrow(() -> new ResourceNotFoundException("Invoice not found."));
    }

    /** Tenant-scoped AND, for employees, restricted to their own submissions. */
    private Invoice findVisible(UUID invoiceId) {
        Invoice invoice = findOwned(invoiceId);
        if (currentUser.role() == OrgRole.EMPLOYEE && !invoice.getSubmittedBy().getId().equals(currentUser.userId())) {
            throw new ResourceNotFoundException("Invoice not found.");
        }
        return invoice;
    }

    private UserAccount currentUserAccount() {
        return userAccountRepository.getReferenceById(currentUser.userId());
    }

    private String sanitizeFilenameForDisplay(String filename) {
        return Path.of(filename).getFileName().toString();
    }

    private InvoiceSummaryResponse toSummaryResponse(Invoice invoice) {
        return new InvoiceSummaryResponse(
            invoice.getId(),
            toVendorSummary(invoice.getVendor()),
            invoice.getInvoiceNumber(),
            invoice.getInvoiceDate(),
            invoice.getDueDate(),
            invoice.getCurrency(),
            invoice.getTotalAmount(),
            invoice.getStatus(),
            invoice.getSubmittedBy().getFullName(),
            invoice.getCreatedAt()
        );
    }

    private InvoiceResponse toDetailResponse(Invoice invoice) {
        List<InvoiceLineItemResponse> lineItems = invoice.getLineItems().stream()
            .map(li -> new InvoiceLineItemResponse(
                li.getId(), li.getLineOrder(), li.getDescription(), li.getQuantity(),
                li.getUnitPrice(), li.getTaxAmount(), li.getDiscountAmount(), li.getTotalAmount()))
            .toList();

        List<InvoiceDocumentResponse> documents = invoice.getDocuments().stream()
            .map(doc -> new InvoiceDocumentResponse(
                doc.getId(), doc.getOriginalFilename(), doc.getContentType(),
                doc.getFileSizeBytes(), doc.getProcessingStatus(), doc.getDocumentType(),
                doc.getOcrConfidence() == null ? null : doc.getOcrConfidence().doubleValue(),
                doc.getRejectionReason(), doc.getCreatedAt()))
            .toList();

        List<Invoice> sameVendorHistory = invoice.getVendor() == null
            ? List.of()
            : invoiceRepository.findByOrganizationIdAndVendorIdAndIdNotAndStatusNot(
                currentUser.organizationId(), invoice.getVendor().getId(), invoice.getId(), InvoiceStatus.ARCHIVED);

        List<ValidationResult> validationResults = invoiceValidationService.validate(invoice);
        List<DuplicateWarning> duplicates = duplicateDetectionService.findDuplicates(invoice, sameVendorHistory);
        Optional<AnomalyFinding> anomaly = anomalyDetectionService.detect(invoice, sameVendorHistory);
        Optional<RecurringExpenseFinding> recurring = invoice.getVendor() == null
            ? Optional.empty()
            : recurringExpenseDetectionService.detect(invoice, sameVendorHistory);
        boolean vendorUnresolved = invoice.getVendor() == null && invoice.getVendorNameRaw() != null;
        RiskScore riskScore = riskScoringService.score(validationResults, duplicates, anomaly, vendorUnresolved);

        List<InvoiceApproval> approvals = invoiceApprovalRepository.findByInvoiceIdOrderByCreatedAtAsc(invoice.getId());
        List<Payment> payments = paymentService.listForInvoice(invoice.getId());
        BigDecimal paidAmount = payments.stream()
            .filter(p -> p.getStatus() == PaymentStatus.COMPLETED)
            .map(Payment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstandingAmount = invoice.getTotalAmount() == null ? null : invoice.getTotalAmount().subtract(paidAmount);
        OrgRole requiredApprovalRole = ApprovalPolicy.requiredApproverRole(invoice.getOrganization(), invoice.getTotalAmount());

        return new InvoiceResponse(
            invoice.getId(),
            toVendorSummary(invoice.getVendor()),
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
            invoice.getDisputeReason(),
            invoice.getFieldConfidence(),
            validationResults.stream().map(r -> new ValidationResultDto(r.rule(), r.status(), r.message())).toList(),
            duplicates.stream().map(d -> new DuplicateWarningDto(d.invoiceId(), d.invoiceNumber(), d.probability(), d.reason())).toList(),
            anomaly.map(a -> new AnomalyDto(a.severity(), a.explanation())).orElse(null),
            recurring.map(r -> new RecurringExpenseDto(r.frequency(), r.occurrences(), r.averageAmount(), r.expectedNextDate(), r.explanation())).orElse(null),
            riskScore.score(),
            riskScore.reasons(),
            requiredApprovalRole,
            approvals.stream().map(a -> new ApprovalDecisionDto(
                a.getId(), a.getDecision(), a.getRequiredRole(), a.getThresholdAmount(), a.getReason(),
                a.getDecidedBy().getFullName(), a.getCreatedAt())).toList(),
            payments.stream().map(p -> new PaymentDto(
                p.getId(), p.getAmount(), p.getCurrency(), p.getMethod(), p.getStatus(), p.getScheduledDate(),
                p.getCompletedAt(), p.getReference(), p.getNotes(), p.getRecordedBy().getFullName(), p.getCreatedAt())).toList(),
            paidAmount,
            outstandingAmount,
            new UserSummaryDto(invoice.getSubmittedBy().getId(), invoice.getSubmittedBy().getEmail(), invoice.getSubmittedBy().getFullName()),
            lineItems,
            documents,
            invoice.getCreatedAt(),
            invoice.getUpdatedAt()
        );
    }

    private VendorSummaryDto toVendorSummary(Vendor vendor) {
        return vendor == null ? null : new VendorSummaryDto(vendor.getId(), vendor.getName());
    }

    public record DownloadableDocument(String filename, String contentType, InputStream content) {
    }
}
