package com.invoiceiq.security;

import com.invoiceiq.entity.OrgRole;
import com.invoiceiq.exception.AccessDeniedApiException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for "who is making this request and in which
 * organization." Service code must derive organizationId from here, never
 * from a request parameter or DTO field.
 */
@Component
public class CurrentUser {

    public AuthenticatedPrincipal get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            throw new AccessDeniedApiException("No authenticated user in context.");
        }
        return principal;
    }

    public UUID organizationId() {
        return get().organizationId();
    }

    public UUID userId() {
        return get().userId();
    }

    public OrgRole role() {
        return get().role();
    }

    public void requireRole(OrgRole... allowed) {
        OrgRole current = role();
        for (OrgRole r : allowed) {
            if (r == current) {
                return;
            }
        }
        throw new AccessDeniedApiException("Your role does not permit this action.");
    }
}
