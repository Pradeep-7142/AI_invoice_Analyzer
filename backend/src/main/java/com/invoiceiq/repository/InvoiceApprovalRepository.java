package com.invoiceiq.repository;

import com.invoiceiq.entity.InvoiceApproval;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceApprovalRepository extends JpaRepository<InvoiceApproval, UUID> {

    List<InvoiceApproval> findByInvoiceIdOrderByCreatedAtAsc(UUID invoiceId);

    boolean existsByInvoiceId(UUID invoiceId);
}
