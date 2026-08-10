package com.invoiceiq.service;

import com.invoiceiq.entity.InvoiceStatus;
import com.invoiceiq.exception.BusinessValidationException;
import java.util.Map;
import java.util.Set;

/**
 * Whitelist of valid invoice status transitions — the single source of
 * truth for "what can happen next," used by every action that mutates
 * invoice status (verify/archive in {@link InvoiceService}, approve/reject/
 * dispute also in {@link InvoiceService}, and the payment-amount-driven
 * moves in {@link PaymentService}) so the graph never has to be
 * reconciled across multiple copies.
 *
 * Payment-driven states (PAYMENT_SCHEDULED/PARTIALLY_PAID/PAID/OVERDUE)
 * are entered by recomputing "how much has actually been paid" rather
 * than by a single discrete user action, but they still have to land on
 * an edge in this graph — see {@code PaymentService.recomputeStatus}.
 */
final class InvoiceLifecycle {

    private static final Map<InvoiceStatus, Set<InvoiceStatus>> ALLOWED_TRANSITIONS = Map.ofEntries(
        Map.entry(InvoiceStatus.UPLOADED, Set.of(InvoiceStatus.NEEDS_REVIEW, InvoiceStatus.ARCHIVED)),
        Map.entry(InvoiceStatus.NEEDS_REVIEW, Set.of(InvoiceStatus.VERIFIED, InvoiceStatus.ARCHIVED)),
        Map.entry(InvoiceStatus.VERIFIED, Set.of(
            InvoiceStatus.NEEDS_REVIEW, InvoiceStatus.PENDING_APPROVAL, InvoiceStatus.APPROVED,
            InvoiceStatus.DISPUTED, InvoiceStatus.ARCHIVED)),
        Map.entry(InvoiceStatus.PENDING_APPROVAL, Set.of(
            InvoiceStatus.APPROVED, InvoiceStatus.NEEDS_REVIEW, InvoiceStatus.DISPUTED, InvoiceStatus.ARCHIVED)),
        Map.entry(InvoiceStatus.APPROVED, Set.of(
            InvoiceStatus.PAYMENT_SCHEDULED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.PAID,
            InvoiceStatus.OVERDUE, InvoiceStatus.DISPUTED, InvoiceStatus.ARCHIVED)),
        Map.entry(InvoiceStatus.PAYMENT_SCHEDULED, Set.of(
            InvoiceStatus.APPROVED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.PAID,
            InvoiceStatus.OVERDUE, InvoiceStatus.DISPUTED, InvoiceStatus.ARCHIVED)),
        Map.entry(InvoiceStatus.PARTIALLY_PAID, Set.of(
            InvoiceStatus.PAID, InvoiceStatus.DISPUTED, InvoiceStatus.ARCHIVED)),
        Map.entry(InvoiceStatus.OVERDUE, Set.of(
            InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.PAID, InvoiceStatus.DISPUTED, InvoiceStatus.ARCHIVED)),
        Map.entry(InvoiceStatus.PAID, Set.of(InvoiceStatus.DISPUTED, InvoiceStatus.ARCHIVED)),
        Map.entry(InvoiceStatus.DISPUTED, Set.of(InvoiceStatus.NEEDS_REVIEW, InvoiceStatus.ARCHIVED)),
        Map.entry(InvoiceStatus.ARCHIVED, Set.of())
    );

    private InvoiceLifecycle() {
    }

    static void assertTransitionAllowed(InvoiceStatus from, InvoiceStatus to) {
        Set<InvoiceStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new BusinessValidationException(
                "Cannot move an invoice from " + from + " to " + to + ".");
        }
    }
}
