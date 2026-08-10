package com.invoiceiq.controller;

import com.invoiceiq.dto.DisputeInvoiceRequest;
import com.invoiceiq.dto.InvoiceResponse;
import com.invoiceiq.dto.InvoiceSummaryResponse;
import com.invoiceiq.dto.InvoiceUpdateRequest;
import com.invoiceiq.dto.RejectInvoiceRequest;
import com.invoiceiq.dto.SchedulePaymentRequest;
import com.invoiceiq.entity.InvoiceStatus;
import com.invoiceiq.service.InvoiceService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private static final String MANAGE_INVOICES = "hasAnyAuthority('ORGANIZATION_ADMIN', 'FINANCE_MANAGER', 'ACCOUNTANT')";
    private static final String UPLOAD_INVOICES = "hasAnyAuthority('ORGANIZATION_ADMIN', 'FINANCE_MANAGER', 'ACCOUNTANT', 'EMPLOYEE')";
    private static final String DECIDE_APPROVALS = "hasAnyAuthority('ORGANIZATION_ADMIN', 'FINANCE_MANAGER')";

    private final InvoiceService invoiceService;
    private final com.invoiceiq.ai.search.NaturalLanguageSearchService naturalLanguageSearchService;

    public InvoiceController(InvoiceService invoiceService, com.invoiceiq.ai.search.NaturalLanguageSearchService naturalLanguageSearchService) {
        this.invoiceService = invoiceService;
        this.naturalLanguageSearchService = naturalLanguageSearchService;
    }

    @PostMapping("/natural-search")
    public com.invoiceiq.dto.AiIntelligenceDto.NaturalSearchResponse naturalSearch(
        @RequestBody com.invoiceiq.dto.AiIntelligenceDto.NaturalSearchRequest request
    ) {
        return naturalLanguageSearchService.search(request);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(UPLOAD_INVOICES)
    public InvoiceResponse upload(@RequestParam("file") MultipartFile file) {
        try {
            return invoiceService.upload(file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read uploaded file.", e);
        }
    }

    @GetMapping
    public Page<InvoiceSummaryResponse> list(
        @RequestParam(required = false) InvoiceStatus status,
        @PageableDefault(size = 25) Pageable pageable
    ) {
        return invoiceService.list(status, pageable);
    }

    @GetMapping("/{invoiceId}")
    public InvoiceResponse get(@PathVariable UUID invoiceId) {
        return invoiceService.get(invoiceId);
    }

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) InvoiceStatus status) {
        byte[] csv = invoiceService.exportCsv(status).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoices.csv\"")
            .body(csv);
    }

    @GetMapping("/{invoiceId}/document")
    public ResponseEntity<InputStreamResource> downloadDocument(@PathVariable UUID invoiceId) {
        var document = invoiceService.downloadLatestDocument(invoiceId);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(document.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + document.filename() + "\"")
            .body(new InputStreamResource(document.content()));
    }

    @PutMapping("/{invoiceId}")
    @PreAuthorize(MANAGE_INVOICES)
    public InvoiceResponse update(@PathVariable UUID invoiceId, @Valid @RequestBody InvoiceUpdateRequest request) {
        return invoiceService.update(invoiceId, request);
    }

    @PostMapping("/{invoiceId}/verify")
    @PreAuthorize(MANAGE_INVOICES)
    public InvoiceResponse verify(@PathVariable UUID invoiceId) {
        return invoiceService.verify(invoiceId);
    }

    @PostMapping("/{invoiceId}/archive")
    @PreAuthorize(MANAGE_INVOICES)
    public InvoiceResponse archive(@PathVariable UUID invoiceId) {
        return invoiceService.archive(invoiceId);
    }

    @PostMapping("/{invoiceId}/submit-for-approval")
    @PreAuthorize(MANAGE_INVOICES)
    public InvoiceResponse submitForApproval(@PathVariable UUID invoiceId) {
        return invoiceService.submitForApproval(invoiceId);
    }

    @PostMapping("/{invoiceId}/approve")
    @PreAuthorize(DECIDE_APPROVALS)
    public InvoiceResponse approve(@PathVariable UUID invoiceId) {
        return invoiceService.approve(invoiceId);
    }

    @PostMapping("/{invoiceId}/reject")
    @PreAuthorize(DECIDE_APPROVALS)
    public InvoiceResponse reject(@PathVariable UUID invoiceId, @Valid @RequestBody RejectInvoiceRequest request) {
        return invoiceService.reject(invoiceId, request);
    }

    @PostMapping("/{invoiceId}/dispute")
    @PreAuthorize(MANAGE_INVOICES)
    public InvoiceResponse dispute(@PathVariable UUID invoiceId, @Valid @RequestBody DisputeInvoiceRequest request) {
        return invoiceService.dispute(invoiceId, request);
    }

    @PostMapping("/{invoiceId}/resolve-dispute")
    @PreAuthorize(MANAGE_INVOICES)
    public InvoiceResponse resolveDispute(@PathVariable UUID invoiceId) {
        return invoiceService.resolveDispute(invoiceId);
    }

    @PostMapping("/{invoiceId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE_INVOICES)
    public InvoiceResponse schedulePayment(@PathVariable UUID invoiceId, @Valid @RequestBody SchedulePaymentRequest request) {
        return invoiceService.schedulePayment(invoiceId, request);
    }

    @PostMapping("/{invoiceId}/payments/{paymentId}/complete")
    @PreAuthorize(MANAGE_INVOICES)
    public InvoiceResponse completePayment(@PathVariable UUID invoiceId, @PathVariable UUID paymentId) {
        return invoiceService.completePayment(invoiceId, paymentId);
    }

    @PostMapping("/{invoiceId}/payments/{paymentId}/cancel")
    @PreAuthorize(MANAGE_INVOICES)
    public InvoiceResponse cancelPayment(@PathVariable UUID invoiceId, @PathVariable UUID paymentId) {
        return invoiceService.cancelPayment(invoiceId, paymentId);
    }
}
