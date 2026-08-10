package com.invoiceiq.dto;

import com.invoiceiq.entity.OrgRole;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    long expiresInSeconds,
    OrgRole role,
    UserSummaryDto user,
    OrganizationSummaryDto organization
) {
}
