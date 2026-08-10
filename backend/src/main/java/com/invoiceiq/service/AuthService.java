package com.invoiceiq.service;

import com.invoiceiq.audit.AuditLogService;
import com.invoiceiq.dto.AuthResponse;
import com.invoiceiq.dto.LoginRequest;
import com.invoiceiq.dto.OrganizationSummaryDto;
import com.invoiceiq.dto.RefreshRequest;
import com.invoiceiq.dto.RegisterRequest;
import com.invoiceiq.dto.UserSummaryDto;
import com.invoiceiq.entity.MembershipStatus;
import com.invoiceiq.entity.OrgRole;
import com.invoiceiq.entity.Organization;
import com.invoiceiq.entity.OrganizationMember;
import com.invoiceiq.entity.RefreshToken;
import com.invoiceiq.entity.UserAccount;
import com.invoiceiq.entity.UserStatus;
import com.invoiceiq.exception.AccessDeniedApiException;
import com.invoiceiq.exception.DuplicateResourceException;
import com.invoiceiq.repository.OrganizationMemberRepository;
import com.invoiceiq.repository.OrganizationRepository;
import com.invoiceiq.repository.RefreshTokenRepository;
import com.invoiceiq.repository.UserAccountRepository;
import com.invoiceiq.security.InvalidTokenException;
import com.invoiceiq.security.JwtService;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final OrganizationRepository organizationRepository;
    private final UserAccountRepository userAccountRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditLogService auditLogService;

    public AuthService(
        OrganizationRepository organizationRepository,
        UserAccountRepository userAccountRepository,
        OrganizationMemberRepository organizationMemberRepository,
        RefreshTokenRepository refreshTokenRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        AuditLogService auditLogService
    ) {
        this.organizationRepository = organizationRepository;
        this.userAccountRepository = userAccountRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);
        if (userAccountRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("An account with this email already exists.");
        }

        Organization organization = new Organization(request.organizationName(), generateUniqueSlug(request.organizationName()));
        organizationRepository.save(organization);

        UserAccount user = new UserAccount(email, passwordEncoder.encode(request.password()), request.fullName());
        userAccountRepository.save(user);

        OrganizationMember membership = new OrganizationMember(organization, user, OrgRole.ORGANIZATION_ADMIN);
        organizationMemberRepository.save(membership);

        auditLogService.record(organization, user, "organization.created", "Organization", organization.getId().toString(), Map.of("name", organization.getName()));
        auditLogService.record(organization, user, "user.registered", "User", user.getId().toString(), Map.of("email", email));

        return issueTokens(user, membership);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);
        UserAccount user = userAccountRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new BadCredentialsException("Invalid email or password."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password.");
        }

        if (user.getStatus() == UserStatus.DISABLED) {
            throw new AccessDeniedApiException("This account has been disabled. Contact your organization administrator.");
        }

        OrganizationMember membership = organizationMemberRepository.findByUserIdAndStatus(user.getId(), MembershipStatus.ACTIVE)
            .orElseThrow(() -> new AccessDeniedApiException("This account is not an active member of any organization."));

        auditLogService.record(membership.getOrganization(), user, "user.login", "User", user.getId().toString(), null);

        return issueTokens(user, membership);
    }

    @Transactional(noRollbackFor = InvalidTokenException.class)
    public AuthResponse refresh(RefreshRequest request) {
        String hash = jwtService.hashToken(request.refreshToken());
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
            .orElseThrow(() -> new InvalidTokenException("Refresh token is invalid."));

        if (existing.getRevokedAt() != null) {
            // Reuse of an already-rotated/revoked token: treat as possible
            // token theft and kill every active session for this user.
            refreshTokenRepository.findByUserIdAndRevokedAtIsNull(existing.getUser().getId())
                .forEach(RefreshToken::revoke);
            throw new InvalidTokenException("Refresh token has already been used. All sessions have been revoked for safety.");
        }

        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException("Refresh token has expired.");
        }

        existing.revoke();

        UserAccount user = existing.getUser();
        OrganizationMember membership = organizationMemberRepository.findByUserIdAndStatus(user.getId(), MembershipStatus.ACTIVE)
            .orElseThrow(() -> new AccessDeniedApiException("This account is not an active member of any organization."));

        return issueTokens(user, membership);
    }

    @Transactional
    public void logout(RefreshRequest request) {
        String hash = jwtService.hashToken(request.refreshToken());
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.revoke();
            }
        });
    }

    private AuthResponse issueTokens(UserAccount user, OrganizationMember membership) {
        Organization organization = membership.getOrganization();
        String accessToken = jwtService.generateAccessToken(user.getId(), organization.getId(), membership.getRole(), user.getEmail());

        String rawRefreshToken = jwtService.generateOpaqueRefreshToken();
        RefreshToken refreshToken = new RefreshToken(user, jwtService.hashToken(rawRefreshToken), jwtService.refreshTokenExpiry());
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(
            accessToken,
            rawRefreshToken,
            jwtService.getAccessTokenTtlSeconds(),
            membership.getRole(),
            new UserSummaryDto(user.getId(), user.getEmail(), user.getFullName()),
            new OrganizationSummaryDto(organization.getId(), organization.getName(), organization.getSlug())
        );
    }

    private String generateUniqueSlug(String organizationName) {
        String base = organizationName.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-+|-+$)", "");
        if (base.isBlank()) {
            base = "organization";
        }

        String candidate = base;
        int suffix = 2;
        while (organizationRepository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        return candidate;
    }
}
