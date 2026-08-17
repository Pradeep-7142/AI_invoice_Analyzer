package com.invoiceiq.dto;

import com.invoiceiq.entity.UserRole;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    long expiresInSeconds,
    UserRole role,
    UserSummaryDto user
) {
}
