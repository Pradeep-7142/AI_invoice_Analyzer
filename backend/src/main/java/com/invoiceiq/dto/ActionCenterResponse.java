package com.invoiceiq.dto;

import java.util.List;

/** The dashboard's "what needs a human right now" summary — every list is a live query, nothing stored. */
public record ActionCenterResponse(
    List<InvoiceSummaryResponse> pendingMyApproval,
    List<InvoiceSummaryResponse> needsMyAttention,
    List<InvoiceSummaryResponse> overdueInvoices,
    List<BudgetStatusResponse> overBudgetCategories
) {
}
