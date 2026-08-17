package com.invoiceiq.security;

import com.invoiceiq.entity.UserAccount;
import com.invoiceiq.entity.UserRole;
import com.invoiceiq.exception.AccessDeniedApiException;
import com.invoiceiq.repository.UserAccountRepository;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    private final UserAccountRepository userAccountRepository;

    public CurrentUser(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    public AuthenticatedPrincipal get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            throw new AccessDeniedApiException("No authenticated user in security context");
        }
        return principal;
    }

    public UUID userId() {
        return get().userId();
    }

    public String email() {
        return get().email();
    }

    public UserRole role() {
        return get().role();
    }

    public boolean isAdmin() {
        return get().role() == UserRole.ROLE_ADMIN;
    }

    public UserAccount entity() {
        return userAccountRepository.findById(userId())
            .orElseThrow(() -> new AccessDeniedApiException("Current user record not found in database"));
    }
}
