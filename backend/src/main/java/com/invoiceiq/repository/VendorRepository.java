package com.invoiceiq.repository;

import com.invoiceiq.entity.Vendor;
import com.invoiceiq.entity.VendorStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorRepository extends JpaRepository<Vendor, UUID> {

    Optional<Vendor> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<Vendor> findByOrganizationIdAndStatusAndNameContainingIgnoreCase(
        UUID organizationId, VendorStatus status, String nameFragment, Pageable pageable);

    Page<Vendor> findByOrganizationIdAndStatus(UUID organizationId, VendorStatus status, Pageable pageable);

    Optional<Vendor> findFirstByOrganizationIdAndStatusAndNameIgnoreCase(
        UUID organizationId, VendorStatus status, String name);
}
