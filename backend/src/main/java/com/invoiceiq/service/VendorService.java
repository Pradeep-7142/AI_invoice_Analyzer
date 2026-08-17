package com.invoiceiq.service;

import com.invoiceiq.audit.AuditLogService;
import com.invoiceiq.dto.VendorRequest;
import com.invoiceiq.dto.VendorResponse;
import com.invoiceiq.dto.VendorSummaryDto;
import com.invoiceiq.entity.Vendor;
import com.invoiceiq.entity.VendorStatus;
import com.invoiceiq.exception.ResourceNotFoundException;
import com.invoiceiq.repository.VendorRepository;
import com.invoiceiq.security.CurrentUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VendorService {

    private final VendorRepository vendorRepository;
    private final CurrentUser currentUser;
    private final AuditLogService auditLogService;

    public VendorService(
        VendorRepository vendorRepository,
        CurrentUser currentUser,
        AuditLogService auditLogService
    ) {
        this.vendorRepository = vendorRepository;
        this.currentUser = currentUser;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public Page<VendorResponse> list(String search, Pageable pageable) {
        Page<Vendor> page = (search == null || search.isBlank())
            ? vendorRepository.findByStatus(VendorStatus.ACTIVE, pageable)
            : vendorRepository.findByStatusAndNameContainingIgnoreCase(VendorStatus.ACTIVE, search.trim(), pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<VendorSummaryDto> listAll() {
        return vendorRepository.findByStatus(VendorStatus.ACTIVE).stream()
            .map(v -> new VendorSummaryDto(v.getId(), v.getName()))
            .toList();
    }

    @Transactional(readOnly = true)
    public VendorResponse get(UUID vendorId) {
        return toResponse(findVendor(vendorId));
    }

    @Transactional
    public VendorResponse create(VendorRequest request) {
        Vendor vendor = new Vendor(request.name());
        applyRequest(vendor, request);
        vendorRepository.save(vendor);

        auditLogService.record(currentUser.entity(), "vendor.created", "Vendor",
            vendor.getId().toString(), Map.of("name", vendor.getName()));

        return toResponse(vendor);
    }

    @Transactional
    public VendorResponse update(UUID vendorId, VendorRequest request) {
        Vendor vendor = findVendor(vendorId);
        applyRequest(vendor, request);
        vendorRepository.save(vendor);

        auditLogService.record(currentUser.entity(), "vendor.updated", "Vendor",
            vendor.getId().toString(), Map.of("name", vendor.getName()));

        return toResponse(vendor);
    }

    @Transactional
    public void archive(UUID vendorId) {
        Vendor vendor = findVendor(vendorId);
        vendor.setStatus(VendorStatus.ARCHIVED);
        vendorRepository.save(vendor);

        auditLogService.record(currentUser.entity(), "vendor.archived", "Vendor",
            vendor.getId().toString(), Map.of("name", vendor.getName()));
    }

    private Vendor findVendor(UUID vendorId) {
        return vendorRepository.findById(vendorId)
            .orElseThrow(() -> new ResourceNotFoundException("Vendor not found with id: " + vendorId));
    }

    private void applyRequest(Vendor vendor, VendorRequest request) {
        vendor.setName(request.name());
        vendor.setEmail(request.email());
        vendor.setPhone(request.phone());
        vendor.setAddress(request.address());
        vendor.setGstin(request.gstin());
        vendor.setTaxId(request.taxId());
        vendor.setCategory(request.category());
        vendor.setNotes(request.notes());
    }

    private VendorResponse toResponse(Vendor vendor) {
        return new VendorResponse(
            vendor.getId(),
            vendor.getName(),
            vendor.getEmail(),
            vendor.getPhone(),
            vendor.getAddress(),
            vendor.getGstin(),
            vendor.getTaxId(),
            vendor.getCategory(),
            vendor.getNotes(),
            vendor.getStatus(),
            vendor.getCreatedAt(),
            vendor.getUpdatedAt()
        );
    }
}
