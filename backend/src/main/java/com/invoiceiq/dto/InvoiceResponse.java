package com.invoiceiq.dto;

import com.invoiceiq.entity.InvoiceStatus;
import com.invoiceiq.entity.OrgRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record InvoiceResponse(
    UUID id,
    VendorSummaryDto vendor,
    String vendorNameRaw,
    String invoiceNumber,
    LocalDate invoiceDate,
    LocalDate dueDate,
    String currency,
    BigDecimal subtotalAmount,
    BigDecimal taxAmount,
    BigDecimal discountAmount,
    BigDecimal totalAmount,
    InvoiceStatus status,
    String notes,
    String disputeReason,
    Map<String, Double> fieldConfidence,
    List<ValidationResultDto> validationResults,
    List<DuplicateWarningDto> duplicateWarnings,
    AnomalyDto anomaly,
    RecurringExpenseDto recurringExpense,
    int riskScore,
    List<String> riskReasons,
    OrgRole requiredApprovalRole,
    List<ApprovalDecisionDto> approvalHistory,
    List<PaymentDto> payments,
    BigDecimal paidAmount,
    BigDecimal outstandingAmount,
    UserSummaryDto submittedBy,
    List<InvoiceLineItemResponse> lineItems,
    List<InvoiceDocumentResponse> documents,
    Instant createdAt,
    Instant updatedAt
) {
}
