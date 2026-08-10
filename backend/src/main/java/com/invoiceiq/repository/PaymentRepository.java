package com.invoiceiq.repository;

import com.invoiceiq.entity.Payment;
import com.invoiceiq.entity.PaymentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByInvoiceIdOrderByScheduledDateAsc(UUID invoiceId);

    Optional<Payment> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<Payment> findByOrganizationIdAndStatusOrderByScheduledDateAsc(UUID organizationId, PaymentStatus status, Pageable pageable);

    Page<Payment> findByOrganizationIdOrderByScheduledDateDesc(UUID organizationId, Pageable pageable);

    /** All of an organization's payments, for cash-flow forecast aggregation. */
    List<Payment> findByOrganizationId(UUID organizationId);
}
