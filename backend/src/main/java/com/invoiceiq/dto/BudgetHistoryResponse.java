package com.invoiceiq.dto;

import java.util.List;
import java.util.UUID;

public record BudgetHistoryResponse(UUID budgetId, String category, String currency, List<BudgetHistoryPoint> points) {
}
