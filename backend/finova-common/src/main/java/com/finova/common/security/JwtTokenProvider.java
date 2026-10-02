package com.finova.common.security;

import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and verifies the Finova access / refresh tokens.
 * <p>
 * The user-service is the only issuer; the api-gateway and the downstream
 * services use the same verification logic so a token minted once is trusted
 * everywhere. Access tokens are short lived and stateless, refresh tokens
 * carry a {@code tokenId} claim so the user-service can revoke them.
 */
public class JwtTokenProvider {

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_TOKEN_TYPE = "typ";
    public static final String CLAIM_TOKEN_ID = "jti";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey signingKey;
    private final String issuer;
    private final long accessTokenTtlSeconds;
    private final long refreshTokenTtlSeconds;

    public JwtTokenProvider(JwtProperties properties) {
        String secret = properties.getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "finova.jwt.secret must be configured with at least 32 bytes (use the JWT_SECRET env var).");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = properties.getIssuer();
        this.accessTokenTtlSeconds = properties.getAccessTokenTtlSeconds();
        this.refreshTokenTtlSeconds = properties.getRefreshTokenTtlSeconds();
    }

    public String createAccessToken(String userId, String email, String role) {
        return build(userId, email, role, TYPE_ACCESS, accessTokenTtlSeconds, UUID.randomUUID().toString());
    }

    public String createRefreshToken(String userId, String email, String role) {
        return build(userId, email, role, TYPE_REFRESH, refreshTokenTtlSeconds, UUID.randomUUID().toString());
    }

    private String build(String userId, String email, String role, String type, long ttlSeconds, String tokenId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(tokenId)
                .issuer(issuer)
                .subject(userId)
                .claims(Map.of(
                        CLAIM_USER_ID, userId,
                        CLAIM_EMAIL, email == null ? "" : email,
                        CLAIM_ROLE, role == null ? "CUSTOMER" : role,
                        CLAIM_TOKEN_TYPE, type))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(signingKey)
                .compact();
    }

    /** Parses and verifies a token, returning its claims. */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, ErrorCode.TOKEN_INVALID.defaultMessage());
        }
    }

    public Optional<String> tryParse(String token) {
        try {
            return Optional.of(parse(token).getSubject());
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    public String extractUserId(String token) {
        return parse(token).getSubject();
    }

    public String extractRole(String token) {
        return parse(token).get(CLAIM_ROLE, String.class);
    }

    public String extractTokenType(String token) {
        return parse(token).get(CLAIM_TOKEN_TYPE, String.class);
    }

    public long expiresInSeconds() {
        return accessTokenTtlSeconds;
    }
}
