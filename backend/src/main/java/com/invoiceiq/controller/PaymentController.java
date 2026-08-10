package com.invoiceiq.controller;

import com.invoiceiq.dto.PaymentSummaryDto;
import com.invoiceiq.entity.PaymentStatus;
import com.invoiceiq.service.PaymentService;
import java.nio.charset.StandardCharsets;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Org-wide payment listing across invoices — the payments-dashboard read path. Mutations live on InvoiceController. */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public Page<PaymentSummaryDto> list(
        @RequestParam(required = false) PaymentStatus status,
        @PageableDefault(size = 25) Pageable pageable
    ) {
        return paymentService.list(status, pageable);
    }

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) PaymentStatus status) {
        byte[] csv = paymentService.exportCsv(status).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"payments.csv\"")
            .body(csv);
    }
}
