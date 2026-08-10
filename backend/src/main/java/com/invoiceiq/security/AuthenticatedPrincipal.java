package com.invoiceiq.security;

import com.invoiceiq.entity.OrgRole;
import java.util.UUID;

/**
 * The tenant-scoped identity resolved from a validated access token.
 * organizationId is ALWAYS derived server-side from the token, never from
 * client-supplied request data — this is what enforces tenant isolation.
 */
public record AuthenticatedPrincipal(
    UUID userId,
    UUID organizationId,
    String email,
    OrgRole role
) {
}
