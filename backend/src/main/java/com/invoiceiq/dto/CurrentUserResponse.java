package com.invoiceiq.dto;

import com.invoiceiq.entity.OrgRole;

public record CurrentUserResponse(
    UserSummaryDto user,
    OrganizationSummaryDto organization,
    OrgRole role
) {
}
