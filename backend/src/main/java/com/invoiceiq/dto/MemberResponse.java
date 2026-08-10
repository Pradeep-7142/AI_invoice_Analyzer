package com.invoiceiq.dto;

import com.invoiceiq.entity.MembershipStatus;
import com.invoiceiq.entity.OrgRole;
import java.time.Instant;
import java.util.UUID;

public record MemberResponse(
    UUID membershipId,
    UUID userId,
    String email,
    String fullName,
    OrgRole role,
    MembershipStatus status,
    Instant createdAt
) {
}
