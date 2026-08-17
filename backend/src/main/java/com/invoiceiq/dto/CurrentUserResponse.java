package com.invoiceiq.dto;

import com.invoiceiq.entity.UserRole;

public record CurrentUserResponse(
    UserSummaryDto user,
    UserRole role
) {
}
