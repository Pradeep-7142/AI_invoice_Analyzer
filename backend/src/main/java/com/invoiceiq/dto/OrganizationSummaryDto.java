package com.invoiceiq.dto;

import java.util.UUID;

public record OrganizationSummaryDto(
    UUID id,
    String name,
    String slug
) {
}
