package com.invoiceiq.controller;

import com.invoiceiq.dto.InvoiceResponse;
import com.invoiceiq.dto.InvoiceSummaryResponse;
import com.invoiceiq.dto.InvoiceUpdateRequest;
import com.invoiceiq.dto.RejectInvoiceRequest;
import com.invoiceiq.entity.InvoiceStatus;
import com.invoiceiq.service.InvoiceService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
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
import org.springframework.web.bind.annotation.DeleteMapping;
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

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
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
        @RequestParam(required = false) String search,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return invoiceService.list(status, search, pageable);
    }

    @GetMapping("/{invoiceId}")
    public InvoiceResponse get(@PathVariable UUID invoiceId) {
        return invoiceService.get(invoiceId);
    }

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) InvoiceStatus status) {
        byte[] csv = invoiceService.exportCsv(status);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoices.csv\"")
            .body(csv);
    }

    @GetMapping("/{invoiceId}/documents/{documentId}")
    public ResponseEntity<InputStreamResource> downloadDocument(
        @PathVariable UUID invoiceId,
        @PathVariable UUID documentId
    ) {
        InputStream stream = invoiceService.downloadDocument(invoiceId, documentId);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
            .body(new InputStreamResource(stream));
    }

    @PutMapping("/{invoiceId}")
    public InvoiceResponse update(@PathVariable UUID invoiceId, @Valid @RequestBody InvoiceUpdateRequest request) {
        return invoiceService.update(invoiceId, request);
    }

    @PostMapping("/{invoiceId}/verify")
    public InvoiceResponse verify(@PathVariable UUID invoiceId) {
        return invoiceService.verify(invoiceId);
    }

    @PostMapping("/{invoiceId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public InvoiceResponse approve(@PathVariable UUID invoiceId) {
        return invoiceService.approve(invoiceId);
    }

    @PostMapping("/{invoiceId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public InvoiceResponse reject(@PathVariable UUID invoiceId, @Valid @RequestBody RejectInvoiceRequest request) {
        return invoiceService.reject(invoiceId, request);
    }

    @PostMapping("/{invoiceId}/archive")
    public InvoiceResponse archive(@PathVariable UUID invoiceId) {
        return invoiceService.archive(invoiceId);
    }

    @DeleteMapping("/{invoiceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID invoiceId) {
        invoiceService.delete(invoiceId);
    }
}
