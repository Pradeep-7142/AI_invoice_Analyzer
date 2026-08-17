package com.invoiceiq.controller;

import com.invoiceiq.dto.VendorRequest;
import com.invoiceiq.dto.VendorResponse;
import com.invoiceiq.dto.VendorSummaryDto;
import com.invoiceiq.service.VendorService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/api/vendors")
public class VendorController {

    private final VendorService vendorService;

    public VendorController(VendorService vendorService) {
        this.vendorService = vendorService;
    }

    @GetMapping
    public Page<VendorResponse> list(
        @RequestParam(required = false) String search,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return vendorService.list(search, pageable);
    }

    @GetMapping("/all")
    public List<VendorSummaryDto> listAll() {
        return vendorService.listAll();
    }

    @GetMapping("/{vendorId}")
    public VendorResponse get(@PathVariable UUID vendorId) {
        return vendorService.get(vendorId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VendorResponse create(@Valid @RequestBody VendorRequest request) {
        return vendorService.create(request);
    }

    @PutMapping("/{vendorId}")
    public VendorResponse update(@PathVariable UUID vendorId, @Valid @RequestBody VendorRequest request) {
        return vendorService.update(vendorId, request);
    }

    @DeleteMapping("/{vendorId}")
    public ResponseEntity<Void> archive(@PathVariable UUID vendorId) {
        vendorService.archive(vendorId);
        return ResponseEntity.noContent().build();
    }
}
