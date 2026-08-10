package com.invoiceiq.service;

import com.invoiceiq.entity.Invoice;
import com.invoiceiq.entity.InvoiceStatus;
import com.invoiceiq.repository.InvoiceRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily sweep that flips unpaid, past-due invoices to OVERDUE. Only
 * APPROVED/PAYMENT_SCHEDULED invoices are candidates — anything with a
 * completed payment already reads as PARTIALLY_PAID/PAID (a more useful
 * status than OVERDUE), and {@link PaymentService#recomputeStatus} applies
 * that same "still overdue if nothing landed yet" rule the moment a
 * payment is scheduled or completed, so this job and payment actions never
 * disagree on the current invoice.
 */
@Component
public class OverdueInvoiceJob {

    private static final List<InvoiceStatus> OVERDUE_CANDIDATE_STATUSES =
        List.of(InvoiceStatus.APPROVED, InvoiceStatus.PAYMENT_SCHEDULED);

    private final InvoiceRepository invoiceRepository;

    public OverdueInvoiceJob(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Scheduled(cron = "0 15 0 * * *")
    public void run() {
        markOverdueInvoices();
    }

    @Transactional
    public int markOverdueInvoices() {
        List<Invoice> candidates = invoiceRepository.findByStatusInAndDueDateBefore(
            OVERDUE_CANDIDATE_STATUSES, LocalDate.now());
        for (Invoice invoice : candidates) {
            InvoiceLifecycle.assertTransitionAllowed(invoice.getStatus(), InvoiceStatus.OVERDUE);
            invoice.setStatus(InvoiceStatus.OVERDUE);
        }
        return candidates.size();
    }
}
