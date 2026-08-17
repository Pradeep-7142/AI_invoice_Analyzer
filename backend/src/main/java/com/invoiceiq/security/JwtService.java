package com.invoiceiq.security;

import com.invoiceiq.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_FULL_NAME = "fullName";

    private final SecretKey signingKey;
    private final long accessTokenTtlMinutes;
    private final long refreshTokenTtlDays;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtService(
        @Value("${invoiceiq.jwt.secret}") String secret,
        @Value("${invoiceiq.jwt.access-token-ttl-minutes}") long accessTokenTtlMinutes,
        @Value("${invoiceiq.jwt.refresh-token-ttl-days}") long refreshTokenTtlDays
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlMinutes = accessTokenTtlMinutes;
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    public String generateAccessToken(UUID userId, String email, String fullName, UserRole role) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(userId.toString())
            .claim(CLAIM_ROLE, role.name())
            .claim(CLAIM_EMAIL, email)
            .claim(CLAIM_FULL_NAME, fullName)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(accessTokenTtlMinutes, ChronoUnit.MINUTES)))
            .signWith(signingKey)
            .compact();
    }

    public AuthenticatedPrincipal parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            return new AuthenticatedPrincipal(
                UUID.fromString(claims.getSubject()),
                claims.get(CLAIM_EMAIL, String.class),
                claims.get(CLAIM_FULL_NAME, String.class),
                UserRole.valueOf(claims.get(CLAIM_ROLE, String.class))
            );
        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException("Access token has expired.");
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Access token is invalid.");
        }
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlMinutes * 60;
    }

    public Instant refreshTokenExpiry() {
        return Instant.now().plus(refreshTokenTtlDays, ChronoUnit.DAYS);
    }

    public String generateOpaqueRefreshToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
