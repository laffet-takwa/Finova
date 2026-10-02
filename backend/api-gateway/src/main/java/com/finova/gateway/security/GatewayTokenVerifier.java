package com.finova.gateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.common.security.AuthenticatedUser;
import com.finova.common.security.JwtProperties;
import com.finova.common.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Verifies the access token with the same {@link JwtTokenProvider} the
 * user-service signs with, so there is exactly one verification implementation
 * in the platform. No database lookup happens on the hot path: the caller
 * identity is read from the token claims.
 * <p>
 * A refresh token presented as an access token is rejected, and an expired token
 * is reported as {@link ErrorCode#TOKEN_EXPIRED} so the SPA knows to call
 * {@code /api/auth/refresh} instead of sending the user back to the login page.
 */
@Component
public class GatewayTokenVerifier {

    private final JwtTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;

    public GatewayTokenVerifier(JwtProperties properties, ObjectMapper objectMapper) {
        this.tokenProvider = new JwtTokenProvider(properties);
        this.objectMapper = objectMapper;
    }

    /**
     * @throws BusinessException with {@link ErrorCode#TOKEN_EXPIRED} or
     *         {@link ErrorCode#TOKEN_INVALID} when the token cannot be trusted
     */
    public AuthenticatedUser verify(String token, String correlationId) {
        Claims claims;
        try {
            claims = tokenProvider.parse(token);
        } catch (BusinessException ex) {
            ErrorCode reason = classify(token);
            throw new BusinessException(reason, reason.defaultMessage());
        }
        String type = claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class);
        if (!JwtTokenProvider.TYPE_ACCESS.equals(type)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID,
                    "A refresh token cannot be used to access the API. Sign in again.");
        }
        return new AuthenticatedUser(
                claims.getSubject(),
                claims.get(JwtTokenProvider.CLAIM_EMAIL, String.class),
                claims.get(JwtTokenProvider.CLAIM_ROLE, String.class),
                correlationId);
    }

    /**
     * Picks the most useful code for a token that already failed cryptographic
     * verification. The payload is only read to choose between two 401 codes;
     * access is never granted from this unverified read.
     */
    private ErrorCode classify(String token) {
        String payload = readPayload(token);
        if (payload == null) {
            return ErrorCode.TOKEN_INVALID;
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            JsonNode expiration = node.get("exp");
            if (expiration != null && expiration.isNumber()
                    && expiration.asLong() < Instant.now().getEpochSecond()) {
                return ErrorCode.TOKEN_EXPIRED;
            }
        } catch (Exception ex) {
            return ErrorCode.TOKEN_INVALID;
        }
        return ErrorCode.TOKEN_INVALID;
    }

    private String readPayload(String token) {
        int firstDot = token.indexOf('.');
        int lastDot = token.lastIndexOf('.');
        if (firstDot < 0 || lastDot <= firstDot) {
            return null;
        }
        try {
            return new String(Base64.getUrlDecoder().decode(token.substring(firstDot + 1, lastDot)),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
