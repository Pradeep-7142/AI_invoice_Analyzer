package com.invoiceiq.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.invoiceiq.entity.OrgRole;
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
        UUID orgId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(userId, orgId, OrgRole.FINANCE_MANAGER, "user@example.com");
        AuthenticatedPrincipal principal = jwtService.parseAccessToken(token);

        assertThat(principal.userId()).isEqualTo(userId);
        assertThat(principal.organizationId()).isEqualTo(orgId);
        assertThat(principal.role()).isEqualTo(OrgRole.FINANCE_MANAGER);
        assertThat(principal.email()).isEqualTo("user@example.com");
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generateAccessToken(UUID.randomUUID(), UUID.randomUUID(), OrgRole.VIEWER, "a@b.com");
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
