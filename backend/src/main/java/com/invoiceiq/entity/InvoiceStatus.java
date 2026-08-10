package com.invoiceiq.entity;

/**
 * Full business lifecycle from the product spec. Phases beyond Invoice Core
 * only exercise a subset of transitions today (see InvoiceLifecycle);
 * the remaining values exist now so later phases (approval, payments)
 * don't require another migration or enum change.
 */
public enum InvoiceStatus {
    UPLOADED,
    PROCESSING,
    NEEDS_REVIEW,
    VERIFIED,
    PENDING_APPROVAL,
    APPROVED,
    PAYMENT_SCHEDULED,
    PARTIALLY_PAID,
    PAID,
    OVERDUE,
    DISPUTED,
    ARCHIVED
}
