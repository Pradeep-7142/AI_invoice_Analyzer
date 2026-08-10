package com.invoiceiq.dto;

import com.invoiceiq.entity.OrgRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(
    @NotNull(message = "Role is required.")
    OrgRole role
) {
}
