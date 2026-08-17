package com.invoiceiq.service;

import com.invoiceiq.audit.AuditLogService;
import com.invoiceiq.dto.AuthResponse;
import com.invoiceiq.dto.CurrentUserResponse;
import com.invoiceiq.dto.LoginRequest;
import com.invoiceiq.dto.RefreshRequest;
import com.invoiceiq.dto.RegisterRequest;
import com.invoiceiq.dto.UserSummaryDto;
import com.invoiceiq.entity.RefreshToken;
import com.invoiceiq.entity.UserAccount;
import com.invoiceiq.entity.UserRole;
import com.invoiceiq.entity.UserStatus;
import com.invoiceiq.exception.AccessDeniedApiException;
import com.invoiceiq.exception.DuplicateResourceException;
import com.invoiceiq.repository.RefreshTokenRepository;
import com.invoiceiq.repository.UserAccountRepository;
import com.invoiceiq.security.CurrentUser;
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

    private final UserAccountRepository userAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditLogService auditLogService;
    private final CurrentUser currentUser;

    public AuthService(
        UserAccountRepository userAccountRepository,
        RefreshTokenRepository refreshTokenRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        AuditLogService auditLogService,
        CurrentUser currentUser
    ) {
        this.userAccountRepository = userAccountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditLogService = auditLogService;
        this.currentUser = currentUser;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);
        if (userAccountRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("An account with this email already exists.");
        }

        // If this is the first user, make them ADMIN; otherwise default to request role or EMPLOYEE
        boolean isFirstUser = userAccountRepository.count() == 0;
        UserRole assignedRole = isFirstUser ? UserRole.ROLE_ADMIN : (request.role() != null ? request.role() : UserRole.ROLE_EMPLOYEE);

        UserAccount user = new UserAccount(email, passwordEncoder.encode(request.password()), request.fullName(), assignedRole);
        userAccountRepository.save(user);

        auditLogService.record(user, "user.registered", "User", user.getId().toString(), Map.of("email", email, "role", assignedRole.name()));

        return issueTokens(user);
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
            throw new AccessDeniedApiException("This account has been disabled. Contact an administrator.");
        }

        auditLogService.record(user, "user.login", "User", user.getId().toString(), null);

        return issueTokens(user);
    }

    @Transactional(noRollbackFor = InvalidTokenException.class)
    public AuthResponse refresh(RefreshRequest request) {
        String hash = jwtService.hashToken(request.refreshToken());
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
            .orElseThrow(() -> new InvalidTokenException("Refresh token is invalid."));

        if (existing.getRevokedAt() != null) {
            refreshTokenRepository.findByUserIdAndRevokedAtIsNull(existing.getUser().getId())
                .forEach(RefreshToken::revoke);
            throw new InvalidTokenException("Refresh token has already been used. All sessions have been revoked for safety.");
        }

        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException("Refresh token has expired.");
        }

        existing.revoke();

        UserAccount user = existing.getUser();
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new AccessDeniedApiException("This account has been disabled.");
        }

        return issueTokens(user);
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

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser() {
        var principal = currentUser.get();
        UserAccount user = currentUser.entity();
        return new CurrentUserResponse(
            new UserSummaryDto(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.getStatus()),
            principal.role()
        );
    }

    private AuthResponse issueTokens(UserAccount user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getFullName(), user.getRole());

        String rawRefreshToken = jwtService.generateOpaqueRefreshToken();
        RefreshToken refreshToken = new RefreshToken(user, jwtService.hashToken(rawRefreshToken), jwtService.refreshTokenExpiry());
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(
            accessToken,
            rawRefreshToken,
            jwtService.getAccessTokenTtlSeconds(),
            user.getRole(),
            new UserSummaryDto(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.getStatus())
        );
    }
}
