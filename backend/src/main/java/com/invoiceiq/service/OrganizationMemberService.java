package com.invoiceiq.service;

import com.invoiceiq.audit.AuditLogService;
import com.invoiceiq.dto.CreateMemberRequest;
import com.invoiceiq.dto.MemberResponse;
import com.invoiceiq.dto.UpdateMemberRoleRequest;
import com.invoiceiq.entity.MembershipStatus;
import com.invoiceiq.entity.OrgRole;
import com.invoiceiq.entity.Organization;
import com.invoiceiq.entity.OrganizationMember;
import com.invoiceiq.entity.UserAccount;
import com.invoiceiq.exception.BusinessValidationException;
import com.invoiceiq.exception.DuplicateResourceException;
import com.invoiceiq.exception.ResourceNotFoundException;
import com.invoiceiq.repository.OrganizationMemberRepository;
import com.invoiceiq.repository.OrganizationRepository;
import com.invoiceiq.repository.UserAccountRepository;
import com.invoiceiq.security.CurrentUser;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every method here scopes to CurrentUser's organizationId — never a
 * client-supplied one — so there is no code path by which one tenant can
 * read or mutate another tenant's members.
 */
@Service
public class OrganizationMemberService {

    private final OrganizationMemberRepository organizationMemberRepository;
    private final OrganizationRepository organizationRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;
    private final AuditLogService auditLogService;

    public OrganizationMemberService(
        OrganizationMemberRepository organizationMemberRepository,
        OrganizationRepository organizationRepository,
        UserAccountRepository userAccountRepository,
        PasswordEncoder passwordEncoder,
        CurrentUser currentUser,
        AuditLogService auditLogService
    ) {
        this.organizationMemberRepository = organizationMemberRepository;
        this.organizationRepository = organizationRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers() {
        UUID organizationId = currentUser.organizationId();
        return organizationMemberRepository.findByOrganizationIdAndStatusOrderByCreatedAtAsc(organizationId, MembershipStatus.ACTIVE)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public MemberResponse addMember(CreateMemberRequest request) {
        UUID organizationId = currentUser.organizationId();
        String email = request.email().toLowerCase(Locale.ROOT);

        if (userAccountRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("An account with this email already exists.");
        }

        Organization organization = organizationRepository.getReferenceById(organizationId);

        UserAccount user = new UserAccount(email, passwordEncoder.encode(request.password()), request.fullName());
        userAccountRepository.save(user);

        OrganizationMember membership = new OrganizationMember(organization, user, request.role());
        organizationMemberRepository.save(membership);

        auditLogService.record(organization, currentUserAccount(), "organization.member_added", "OrganizationMember",
            membership.getId().toString(), Map.of("email", email, "role", request.role().name()));

        return toResponse(membership);
    }

    @Transactional
    public MemberResponse updateRole(UUID membershipId, UpdateMemberRoleRequest request) {
        UUID organizationId = currentUser.organizationId();
        OrganizationMember membership = organizationMemberRepository.findByIdAndOrganizationId(membershipId, organizationId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization member not found."));

        OrgRole previousRole = membership.getRole();
        if (previousRole == OrgRole.ORGANIZATION_ADMIN && request.role() != OrgRole.ORGANIZATION_ADMIN) {
            requireMoreThanOneActiveAdmin(organizationId);
        }

        membership.setRole(request.role());

        auditLogService.record(membership.getOrganization(), currentUserAccount(), "organization.member_role_changed", "OrganizationMember",
            membership.getId().toString(), Map.of("from", previousRole.name(), "to", request.role().name()));

        return toResponse(membership);
    }

    @Transactional
    public void removeMember(UUID membershipId) {
        UUID organizationId = currentUser.organizationId();
        OrganizationMember membership = organizationMemberRepository.findByIdAndOrganizationId(membershipId, organizationId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization member not found."));

        if (membership.getUser().getId().equals(currentUser.userId())) {
            throw new BusinessValidationException("You cannot remove yourself from the organization.");
        }

        if (membership.getRole() == OrgRole.ORGANIZATION_ADMIN) {
            requireMoreThanOneActiveAdmin(organizationId);
        }

        membership.setStatus(MembershipStatus.REMOVED);

        auditLogService.record(membership.getOrganization(), currentUserAccount(), "organization.member_removed", "OrganizationMember",
            membership.getId().toString(), Map.of("email", membership.getUser().getEmail()));
    }

    private void requireMoreThanOneActiveAdmin(UUID organizationId) {
        long activeAdmins = organizationMemberRepository.countByOrganizationIdAndRoleAndStatus(
            organizationId, OrgRole.ORGANIZATION_ADMIN, MembershipStatus.ACTIVE);
        if (activeAdmins <= 1) {
            throw new BusinessValidationException("An organization must have at least one administrator.");
        }
    }

    private UserAccount currentUserAccount() {
        return userAccountRepository.getReferenceById(currentUser.userId());
    }

    private MemberResponse toResponse(OrganizationMember membership) {
        UserAccount user = membership.getUser();
        return new MemberResponse(
            membership.getId(),
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            membership.getRole(),
            membership.getStatus(),
            membership.getCreatedAt()
        );
    }
}
