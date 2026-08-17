package com.invoiceiq.repository;

import com.invoiceiq.entity.Vendor;
import com.invoiceiq.entity.VendorStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorRepository extends JpaRepository<Vendor, UUID> {

    List<Vendor> findByStatus(VendorStatus status);

    Page<Vendor> findByStatusAndNameContainingIgnoreCase(VendorStatus status, String nameFragment, Pageable pageable);

    Page<Vendor> findByStatus(VendorStatus status, Pageable pageable);

    Page<Vendor> findByNameContainingIgnoreCase(String nameFragment, Pageable pageable);

    Optional<Vendor> findFirstByStatusAndNameIgnoreCase(VendorStatus status, String name);

    Optional<Vendor> findFirstByNameIgnoreCase(String name);
}
