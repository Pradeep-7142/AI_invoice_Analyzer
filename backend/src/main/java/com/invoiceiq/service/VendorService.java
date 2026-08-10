package com.invoiceiq.service;

import com.invoiceiq.audit.AuditLogService;
import com.invoiceiq.dto.VendorRequest;
import com.invoiceiq.dto.VendorResponse;
import com.invoiceiq.entity.Organization;
import com.invoiceiq.entity.UserAccount;
import com.invoiceiq.entity.Vendor;
import com.invoiceiq.entity.VendorStatus;
import com.invoiceiq.exception.ResourceNotFoundException;
import com.invoiceiq.repository.OrganizationRepository;
import com.invoiceiq.repository.UserAccountRepository;
import com.invoiceiq.repository.VendorRepository;
import com.invoiceiq.security.CurrentUser;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VendorService {

    private final VendorRepository vendorRepository;
    private final OrganizationRepository organizationRepository;
    private final UserAccountRepository userAccountRepository;
    private final CurrentUser currentUser;
    private final AuditLogService auditLogService;

    public VendorService(
        VendorRepository vendorRepository,
        OrganizationRepository organizationRepository,
        UserAccountRepository userAccountRepository,
        CurrentUser currentUser,
        AuditLogService auditLogService
    ) {
        this.vendorRepository = vendorRepository;
        this.organizationRepository = organizationRepository;
        this.userAccountRepository = userAccountRepository;
        this.currentUser = currentUser;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public Page<VendorResponse> list(String search, Pageable pageable) {
        UUID organizationId = currentUser.organizationId();
        Page<Vendor> page = (search == null || search.isBlank())
            ? vendorRepository.findByOrganizationIdAndStatus(organizationId, VendorStatus.ACTIVE, pageable)
            : vendorRepository.findByOrganizationIdAndStatusAndNameContainingIgnoreCase(
                organizationId, VendorStatus.ACTIVE, search.trim(), pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public VendorResponse get(UUID vendorId) {
        return toResponse(findOwned(vendorId));
    }

    @Transactional
    public VendorResponse create(VendorRequest request) {
        Organization organization = organizationRepository.getReferenceById(currentUser.organizationId());
        Vendor vendor = new Vendor(organization, request.name());
        applyRequest(vendor, request);
        vendorRepository.save(vendor);

        auditLogService.record(organization, currentUserAccount(), "vendor.created", "Vendor",
            vendor.getId().toString(), Map.of("name", vendor.getName()));

        return toResponse(vendor);
    }

    @Transactional
    public VendorResponse update(UUID vendorId, VendorRequest request) {
        Vendor vendor = findOwned(vendorId);
        applyRequest(vendor, request);

        auditLogService.record(vendor.getOrganization(), currentUserAccount(), "vendor.updated", "Vendor",
            vendor.getId().toString(), Map.of("name", vendor.getName()));

        return toResponse(vendor);
    }

    @Transactional
    public void archive(UUID vendorId) {
        Vendor vendor = findOwned(vendorId);
        vendor.setStatus(VendorStatus.ARCHIVED);

        auditLogService.record(vendor.getOrganization(), currentUserAccount(), "vendor.archived", "Vendor",
            vendor.getId().toString(), Map.of("name", vendor.getName()));
    }

    private Vendor findOwned(UUID vendorId) {
        return vendorRepository.findByIdAndOrganizationId(vendorId, currentUser.organizationId())
            .orElseThrow(() -> new ResourceNotFoundException("Vendor not found."));
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

    private UserAccount currentUserAccount() {
        return userAccountRepository.getReferenceById(currentUser.userId());
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
