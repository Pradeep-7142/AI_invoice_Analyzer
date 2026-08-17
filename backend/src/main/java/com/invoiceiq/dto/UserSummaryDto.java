package com.invoiceiq.dto;

import com.invoiceiq.entity.UserRole;
import com.invoiceiq.entity.UserStatus;
import java.util.UUID;

public record UserSummaryDto(
    UUID id,
    String email,
    String fullName,
    UserRole role,
    UserStatus status
) {
    public UserSummaryDto(UUID id, String email, String fullName) {
        this(id, email, fullName, UserRole.ROLE_EMPLOYEE, UserStatus.ACTIVE);
    }
}
