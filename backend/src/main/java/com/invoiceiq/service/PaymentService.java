package com.invoiceiq.service;

import com.invoiceiq.audit.AuditLogService;
import com.invoiceiq.dto.PaymentSummaryDto;
import com.invoiceiq.dto.SchedulePaymentRequest;
import com.invoiceiq.entity.Invoice;
import com.invoiceiq.entity.InvoiceStatus;
import com.invoiceiq.entity.Payment;
import com.invoiceiq.entity.PaymentStatus;
import com.invoiceiq.entity.UserAccount;
import com.invoiceiq.exception.BusinessValidationException;
import com.invoiceiq.exception.ResourceNotFoundException;
import com.invoiceiq.export.CsvWriter;
import com.invoiceiq.repository.PaymentRepository;
import com.invoiceiq.security.CurrentUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns payment mechanics for a single invoice (schedule/complete/cancel)
 * plus the org-wide payment listing. Invoice status while money is moving
 * is *derived*, not chosen — {@link #recomputeStatus} looks at what's
 * actually been paid and picks the honest status, still going through
 * {@link InvoiceLifecycle} so the transition graph stays in one place.
 * The invoice-scoped mutators are package-private: {@link InvoiceService}
 * is the public API for "things that can happen to an invoice," this class
 * is its payments engine.
 */
@Service
public class PaymentService {

    private static final Set<InvoiceStatus> PAYABLE_STATUSES = Set.of(
        InvoiceStatus.APPROVED, InvoiceStatus.PAYMENT_SCHEDULED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.OVERDUE);

    private final PaymentRepository paymentRepository;
    private final CurrentUser currentUser;
    private final AuditLogService auditLogService;

    public PaymentService(PaymentRepository paymentRepository, CurrentUser currentUser, AuditLogService auditLogService) {
        this.paymentRepository = paymentRepository;
        this.currentUser = currentUser;
        this.auditLogService = auditLogService;
    }

    Payment schedule(Invoice invoice, SchedulePaymentRequest request, UserAccount actor) {
        requirePayable(invoice);
        BigDecimal outstanding = outstandingAmount(invoice);
        if (request.amount().compareTo(outstanding) > 0) {
            throw new BusinessValidationException(
                "Payment amount " + request.amount() + " exceeds the outstanding balance of " + outstanding + " " + invoice.getCurrency() + ".");
        }

        Payment payment = new Payment(
            invoice.getOrganization(), invoice, request.amount(), invoice.getCurrency(), request.method(),
            request.scheduledDate(), request.reference(), request.notes(), actor);
        paymentRepository.save(payment);
        recomputeStatus(invoice);

        auditLogService.record(invoice.getOrganization(), actor, "payment.scheduled", "Invoice", invoice.getId().toString(),
            Map.of("amount", payment.getAmount().toString(), "scheduledDate", payment.getScheduledDate().toString()));

        return payment;
    }

    Payment complete(Invoice invoice, UUID paymentId, UserAccount actor) {
        Payment payment = getOwnedPayment(invoice, paymentId);
        if (payment.getStatus() != PaymentStatus.SCHEDULED) {
            throw new BusinessValidationException("Only a scheduled payment can be marked completed.");
        }
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setCompletedAt(Instant.now());
        recomputeStatus(invoice);

        auditLogService.record(invoice.getOrganization(), actor, "payment.completed", "Invoice", invoice.getId().toString(),
            Map.of("amount", payment.getAmount().toString()));

        return payment;
    }

    Payment cancel(Invoice invoice, UUID paymentId, UserAccount actor) {
        Payment payment = getOwnedPayment(invoice, paymentId);
        if (payment.getStatus() != PaymentStatus.SCHEDULED) {
            throw new BusinessValidationException("Only a scheduled payment can be cancelled.");
        }
        payment.setStatus(PaymentStatus.CANCELLED);
        recomputeStatus(invoice);

        auditLogService.record(invoice.getOrganization(), actor, "payment.cancelled", "Invoice", invoice.getId().toString(),
            Map.of("amount", payment.getAmount().toString()));

        return payment;
    }

    List<Payment> listForInvoice(UUID invoiceId) {
        return paymentRepository.findByInvoiceIdOrderByScheduledDateAsc(invoiceId);
    }

    BigDecimal paidAmount(Invoice invoice) {
        return listForInvoice(invoice.getId()).stream()
            .filter(p -> p.getStatus() == PaymentStatus.COMPLETED)
            .map(Payment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal outstandingAmount(Invoice invoice) {
        BigDecimal total = invoice.getTotalAmount() == null ? BigDecimal.ZERO : invoice.getTotalAmount();
        return total.subtract(paidAmount(invoice));
    }

    private void requirePayable(Invoice invoice) {
        if (!PAYABLE_STATUSES.contains(invoice.getStatus())) {
            throw new BusinessValidationException(
                "Cannot record a payment on an invoice with status " + invoice.getStatus() + ".");
        }
    }

    /** Recomputes status from actual payment facts, then routes the move through the one lifecycle whitelist. */
    void recomputeStatus(Invoice invoice) {
        List<Payment> payments = listForInvoice(invoice.getId());
        BigDecimal completedSum = payments.stream()
            .filter(p -> p.getStatus() == PaymentStatus.COMPLETED)
            .map(Payment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean hasActiveScheduled = payments.stream().anyMatch(p -> p.getStatus() == PaymentStatus.SCHEDULED);
        BigDecimal total = invoice.getTotalAmount() == null ? BigDecimal.ZERO : invoice.getTotalAmount();
        boolean pastDue = invoice.getDueDate() != null && invoice.getDueDate().isBefore(LocalDate.now());

        InvoiceStatus target;
        if (total.compareTo(BigDecimal.ZERO) > 0 && completedSum.compareTo(total) >= 0) {
            target = InvoiceStatus.PAID;
        } else if (completedSum.compareTo(BigDecimal.ZERO) > 0) {
            target = InvoiceStatus.PARTIALLY_PAID;
        } else if (pastDue) {
            target = InvoiceStatus.OVERDUE;
        } else if (hasActiveScheduled) {
            target = InvoiceStatus.PAYMENT_SCHEDULED;
        } else {
            target = InvoiceStatus.APPROVED;
        }

        if (target != invoice.getStatus()) {
            InvoiceLifecycle.assertTransitionAllowed(invoice.getStatus(), target);
            invoice.setStatus(target);
        }
    }

    private Payment getOwnedPayment(Invoice invoice, UUID paymentId) {
        return paymentRepository.findById(paymentId)
            .filter(p -> p.getInvoice().getId().equals(invoice.getId()))
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found on this invoice."));
    }

    @Transactional(readOnly = true)
    public Page<PaymentSummaryDto> list(PaymentStatus statusFilter, Pageable pageable) {
        UUID organizationId = currentUser.organizationId();
        Page<Payment> page = statusFilter == null
            ? paymentRepository.findByOrganizationIdOrderByScheduledDateDesc(organizationId, pageable)
            : paymentRepository.findByOrganizationIdAndStatusOrderByScheduledDateAsc(organizationId, statusFilter, pageable);
        return page.map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public String exportCsv(PaymentStatus statusFilter) {
        UUID organizationId = currentUser.organizationId();
        List<Payment> payments = paymentRepository.findByOrganizationId(organizationId).stream()
            .filter(p -> statusFilter == null || p.getStatus() == statusFilter)
            .toList();

        StringBuilder csv = new StringBuilder(CsvWriter.row(
            "Invoice Number", "Vendor", "Amount", "Currency", "Method", "Status", "Scheduled Date", "Completed At", "Reference"));
        for (Payment payment : payments) {
            Invoice invoice = payment.getInvoice();
            csv.append(CsvWriter.row(
                invoice.getInvoiceNumber(),
                invoice.getVendor() != null ? invoice.getVendor().getName() : invoice.getVendorNameRaw(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getScheduledDate(),
                payment.getCompletedAt(),
                payment.getReference()));
        }
        return csv.toString();
    }

    private PaymentSummaryDto toSummary(Payment payment) {
        Invoice invoice = payment.getInvoice();
        return new PaymentSummaryDto(
            payment.getId(),
            invoice.getId(),
            invoice.getInvoiceNumber(),
            invoice.getVendor() != null ? invoice.getVendor().getName() : invoice.getVendorNameRaw(),
            payment.getAmount(),
            payment.getCurrency(),
            payment.getMethod(),
            payment.getStatus(),
            payment.getScheduledDate(),
            payment.getCompletedAt(),
            payment.getCreatedAt()
        );
    }
}
