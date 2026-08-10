package com.invoiceiq.dto;

import com.invoiceiq.entity.ApprovalDecisionType;
import com.invoiceiq.entity.OrgRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ApprovalDecisionDto(
    UUID id,
    ApprovalDecisionType decision,
    OrgRole requiredRole,
    BigDecimal thresholdAmount,
    String reason,
    String decidedByName,
    Instant createdAt
) {
}
