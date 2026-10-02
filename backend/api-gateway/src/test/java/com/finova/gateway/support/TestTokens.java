package com.finova.gateway.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finova.common.security.JwtProperties;
import com.finova.common.security.JwtTokenProvider;

/**
 * Fixtures shared by the gateway tests: the secret the tests sign tokens with, a
 * provider for valid tokens and one that mints already expired tokens, plus the
 * object mapper the production error responder uses.
 */
public final class TestTokens {

    public static final String SECRET = "finova-gateway-spec-secret-key-32-bytes-minimum";
    public static final String ISSUER = "finova";

    private TestTokens() {
    }

    public static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public static JwtProperties properties() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setIssuer(ISSUER);
        properties.setAccessTokenTtlSeconds(3600L);
        properties.setRefreshTokenTtlSeconds(604800L);
        return properties;
    }

    public static JwtTokenProvider provider() {
        return new JwtTokenProvider(properties());
    }

    public static JwtTokenProvider expiringProvider() {
        JwtProperties properties = properties();
        properties.setAccessTokenTtlSeconds(-120L);
        return new JwtTokenProvider(properties);
    }

    public static JwtTokenProvider foreignSecretProvider() {
        JwtProperties properties = properties();
        properties.setSecret("a-completely-different-secret-key-32-bytes-x");
        return new JwtTokenProvider(properties);
    }

    public static String accessToken(String userId, String role) {
        return provider().createAccessToken(userId, userId + "@finova.dev", role);
    }

    public static String refreshToken(String userId, String role) {
        return provider().createRefreshToken(userId, userId + "@finova.dev", role);
    }

    public static String expiredAccessToken(String userId, String role) {
        return expiringProvider().createAccessToken(userId, userId + "@finova.dev", role);
    }

    public static String foreignAccessToken(String userId, String role) {
        return foreignSecretProvider().createAccessToken(userId, userId + "@finova.dev", role);
    }
}
