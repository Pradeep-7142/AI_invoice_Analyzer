package com.invoiceiq.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.invoiceiq.entity.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
        "unit-test-secret-key-that-is-long-enough-for-hs256-signing",
        15,
        7
    );

    @Test
    void accessTokenRoundTripsAllClaims() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(userId, "admin@example.com", "Admin User", UserRole.ROLE_ADMIN);
        AuthenticatedPrincipal principal = jwtService.parseAccessToken(token);

        assertThat(principal.userId()).isEqualTo(userId);
        assertThat(principal.role()).isEqualTo(UserRole.ROLE_ADMIN);
        assertThat(principal.email()).isEqualTo("admin@example.com");
        assertThat(principal.fullName()).isEqualTo("Admin User");
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generateAccessToken(UUID.randomUUID(), "employee@example.com", "Employee User", UserRole.ROLE_EMPLOYEE);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> jwtService.parseAccessToken(tampered))
            .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refreshTokenHashIsDeterministicAndDistinctFromRawValue() {
        String raw = jwtService.generateOpaqueRefreshToken();
        String hash1 = jwtService.hashToken(raw);
        String hash2 = jwtService.hashToken(raw);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).isNotEqualTo(raw);
    }
}
