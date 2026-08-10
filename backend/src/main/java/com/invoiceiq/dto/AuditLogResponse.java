package com.invoiceiq.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
    UUID id,
    String actorEmail,
    String action,
    String entityType,
    String entityId,
    Map<String, Object> metadata,
    Instant createdAt
) {
}
