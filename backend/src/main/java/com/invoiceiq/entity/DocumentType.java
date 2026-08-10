package com.invoiceiq.entity;

/**
 * Result of heuristic document classification. Only INVOICE, RECEIPT,
 * CREDIT_NOTE and DEBIT_NOTE are invoice-compatible — PURCHASE_ORDER and
 * STATEMENT are real documents but the wrong kind for this workflow, and
 * OTHER/UNKNOWN mean the classifier found no confident signal either way.
 */
public enum DocumentType {
    INVOICE,
    RECEIPT,
    CREDIT_NOTE,
    DEBIT_NOTE,
    PURCHASE_ORDER,
    STATEMENT,
    OTHER,
    UNKNOWN
}
