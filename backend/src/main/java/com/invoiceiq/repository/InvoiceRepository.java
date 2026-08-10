package com.invoiceiq.repository;

import com.invoiceiq.entity.Invoice;
import com.invoiceiq.entity.InvoiceStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<Invoice> findByOrganizationId(UUID organizationId, Pageable pageable);

    Page<Invoice> findByOrganizationIdAndStatus(UUID organizationId, InvoiceStatus status, Pageable pageable);

    Page<Invoice> findByOrganizationIdAndSubmittedById(UUID organizationId, UUID submittedById, Pageable pageable);

    Page<Invoice> findByOrganizationIdAndSubmittedByIdAndStatus(
        UUID organizationId, UUID submittedById, InvoiceStatus status, Pageable pageable);

    /** Unpaged variants for CSV export and dashboard aggregation, where every matching row is wanted at once. */
    List<Invoice> findByOrganizationIdAndStatus(UUID organizationId, InvoiceStatus status);

    List<Invoice> findByOrganizationIdAndSubmittedById(UUID organizationId, UUID submittedById);

    List<Invoice> findByOrganizationIdAndSubmittedByIdAndStatus(UUID organizationId, UUID submittedById, InvoiceStatus status);

    /** Other invoices from the same vendor, used for both duplicate and anomaly detection. */
    List<Invoice> findByOrganizationIdAndVendorIdAndIdNotAndStatusNot(
        UUID organizationId, UUID vendorId, UUID excludeId, InvoiceStatus excludedStatus);

    /** All invoices dated within a window, used for budget actual-spend and recurring-expense checks. */
    List<Invoice> findByOrganizationIdAndInvoiceDateBetween(UUID organizationId, LocalDate start, LocalDate end);

    /** Payable-but-unpaid invoices that may have crossed their due date, for the daily overdue sweep. */
    List<Invoice> findByStatusInAndDueDateBefore(List<InvoiceStatus> statuses, LocalDate date);

    /** The whole organization's invoices, for analytics/forecast aggregation (status counts, outstanding, due dates). */
    List<Invoice> findByOrganizationId(UUID organizationId);
}
