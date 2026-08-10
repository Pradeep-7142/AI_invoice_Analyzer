package com.invoiceiq.service;

import com.invoiceiq.dto.CurrentUserResponse;
import com.invoiceiq.dto.OrganizationSummaryDto;
import com.invoiceiq.dto.UserSummaryDto;
import com.invoiceiq.entity.Organization;
import com.invoiceiq.entity.UserAccount;
import com.invoiceiq.exception.ResourceNotFoundException;
import com.invoiceiq.repository.OrganizationRepository;
import com.invoiceiq.repository.UserAccountRepository;
import com.invoiceiq.security.AuthenticatedPrincipal;
import com.invoiceiq.security.CurrentUser;
import org.springframework.stereotype.Service;

@Service
public class UserQueryService {

    private final CurrentUser currentUser;
    private final UserAccountRepository userAccountRepository;
    private final OrganizationRepository organizationRepository;

    public UserQueryService(CurrentUser currentUser, UserAccountRepository userAccountRepository, OrganizationRepository organizationRepository) {
        this.currentUser = currentUser;
        this.userAccountRepository = userAccountRepository;
        this.organizationRepository = organizationRepository;
    }

    public CurrentUserResponse currentUser() {
        AuthenticatedPrincipal principal = currentUser.get();

        UserAccount user = userAccountRepository.findById(principal.userId())
            .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        Organization organization = organizationRepository.findById(principal.organizationId())
            .orElseThrow(() -> new ResourceNotFoundException("Organization not found."));

        return new CurrentUserResponse(
            new UserSummaryDto(user.getId(), user.getEmail(), user.getFullName()),
            new OrganizationSummaryDto(organization.getId(), organization.getName(), organization.getSlug()),
            principal.role()
        );
    }
}
