package com.invoiceiq.repository;

import com.invoiceiq.entity.Invoice;
import com.invoiceiq.entity.InvoiceStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Page<Invoice> findByStatus(InvoiceStatus status, Pageable pageable);

    Page<Invoice> findBySubmittedById(UUID submittedById, Pageable pageable);

    Page<Invoice> findBySubmittedByIdAndStatus(UUID submittedById, InvoiceStatus status, Pageable pageable);

    List<Invoice> findByStatus(InvoiceStatus status);

    List<Invoice> findBySubmittedById(UUID submittedById);

    List<Invoice> findBySubmittedByIdAndStatus(UUID submittedById, InvoiceStatus status);

    /** Invoices from same vendor for duplicate checking */
    List<Invoice> findByVendorIdAndIdNotAndStatusNot(UUID vendorId, UUID excludeId, InvoiceStatus excludedStatus);

    @Query("SELECT i FROM Invoice i WHERE (:status IS NULL OR i.status = :status) AND " +
           "(LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(i.vendorNameRaw) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " (i.vendor IS NOT NULL AND LOWER(i.vendor.name) LIKE LOWER(CONCAT('%', :search, '%'))))")
    Page<Invoice> searchInvoices(@Param("status") InvoiceStatus status, @Param("search") String search, Pageable pageable);

    @Query("SELECT i FROM Invoice i WHERE i.submittedBy.id = :userId AND (:status IS NULL OR i.status = :status) AND " +
           "(LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(i.vendorNameRaw) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " (i.vendor IS NOT NULL AND LOWER(i.vendor.name) LIKE LOWER(CONCAT('%', :search, '%'))))")
    Page<Invoice> searchInvoicesForUser(@Param("userId") UUID userId, @Param("status") InvoiceStatus status, @Param("search") String search, Pageable pageable);

    long countByStatus(InvoiceStatus status);
}
