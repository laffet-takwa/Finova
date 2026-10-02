package com.finova.user.security;

import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.common.security.JwtProperties;
import com.finova.common.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The token contract every other Finova component depends on.
 * <p>
 * The gateway and all five downstream services verify a token minted here with the
 * same secret and the same issuer, so a mistake in this class is a platform-wide
 * outage rather than a local bug. That is why these are plain unit tests with no
 * Spring context: the class has no framework dependency worth starting one for, and
 * the three cases that matter are signing, a foreign signature, and expiry.
 */
class JwtTokenProviderTest {

    private static final String SECRET = "finova-test-secret-key-that-is-long-enough-32b";
    private static final String OTHER_SECRET = "a-completely-different-secret-key-32bytes";
    private static final String ISSUER = "finova";
    private static final String USER_ID = "3f6d9a1c-4b7e-4f0a-9c2d-8e5f1a2b3c4d";

    @Test
    @DisplayName("An issued access token carries the identity claims the gateway reads")
    void shouldSignAndParseAnAccessToken() {
        JwtTokenProvider provider = provider(SECRET, 3600L);

        Claims claims = provider.parse(provider.createAccessToken(USER_ID, "takwa@finova.dev", "CUSTOMER"));

        assertThat(claims.getSubject()).isEqualTo(USER_ID);
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.get(JwtTokenProvider.CLAIM_USER_ID, String.class)).isEqualTo(USER_ID);
        assertThat(claims.get(JwtTokenProvider.CLAIM_EMAIL, String.class)).isEqualTo("takwa@finova.dev");
        assertThat(claims.get(JwtTokenProvider.CLAIM_ROLE, String.class)).isEqualTo("CUSTOMER");
        assertThat(claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class))
                .isEqualTo(JwtTokenProvider.TYPE_ACCESS);
        assertThat(claims.getId()).isNotBlank();
    }

    @Test
    @DisplayName("The two token types are distinguishable, which is what keeps them from swapping roles")
    void shouldDistinguishAccessFromRefreshTokens() {
        JwtTokenProvider provider = provider(SECRET, 3600L);

        assertThat(provider.extractTokenType(provider.createAccessToken(USER_ID, "a@b.dev", "CUSTOMER")))
                .isEqualTo(JwtTokenProvider.TYPE_ACCESS);
        assertThat(provider.extractTokenType(provider.createRefreshToken(USER_ID, "a@b.dev", "CUSTOMER")))
                .isEqualTo(JwtTokenProvider.TYPE_REFRESH);
        assertThat(provider.extractUserId(provider.createAccessToken(USER_ID, "a@b.dev", "ADMIN")))
                .isEqualTo(USER_ID);
        assertThat(provider.extractRole(provider.createAccessToken(USER_ID, "a@b.dev", "ADMIN")))
                .isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("Every issued token gets its own jti so two sessions can be revoked separately")
    void shouldMintADistinctTokenIdPerToken() {
        JwtTokenProvider provider = provider(SECRET, 3600L);

        String first = provider.parse(provider.createRefreshToken(USER_ID, "a@b.dev", "CUSTOMER")).getId();
        String second = provider.parse(provider.createRefreshToken(USER_ID, "a@b.dev", "CUSTOMER")).getId();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("A token signed with a different secret is refused with TOKEN_INVALID")
    void shouldRejectATokenSignedWithAnotherSecret() {
        String foreignToken = provider(OTHER_SECRET, 3600L)
                .createAccessToken(USER_ID, "takwa@finova.dev", "ADMIN");

        assertThatThrownBy(() -> provider(SECRET, 3600L).parse(foreignToken))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    @DisplayName("A token whose issuer is not this platform is refused with TOKEN_INVALID")
    void shouldRejectATokenFromAnotherIssuer() {
        JwtProperties other = properties(SECRET, 3600L);
        other.setIssuer("somebody-else");
        String token = new JwtTokenProvider(other).createAccessToken(USER_ID, "a@b.dev", "CUSTOMER");

        assertThatThrownBy(() -> provider(SECRET, 3600L).parse(token))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    @DisplayName("An expired token is refused with TOKEN_INVALID")
    void shouldRejectAnExpiredToken() {
        // A negative TTL mints a token that was already past its expiry when it was
        // signed, which is exactly the state a lapsed session token arrives in.
        JwtTokenProvider issuer = provider(SECRET, -60L);
        String expired = issuer.createRefreshToken(USER_ID, "takwa@finova.dev", "CUSTOMER");

        assertThatThrownBy(() -> provider(SECRET, 3600L).parse(expired))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    @DisplayName("tryParse answers empty instead of throwing on an unusable token")
    void shouldAnswerEmptyFromTryParseOnGarbage() {
        JwtTokenProvider provider = provider(SECRET, 3600L);

        assertThat(provider.tryParse("not-a-jwt")).isEmpty();
        assertThat(provider.tryParse(provider.createAccessToken(USER_ID, "a@b.dev", "CUSTOMER")))
                .contains(USER_ID);
    }

    @Test
    @DisplayName("A secret shorter than the HS256 key length is a start-up failure, not a weak token")
    void shouldRefuseToStartWithATooShortSecret() {
        assertThatThrownBy(() -> provider("too-short", 3600L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    private static JwtTokenProvider provider(String secret, long ttlSeconds) {
        return new JwtTokenProvider(properties(secret, ttlSeconds));
    }

    private static JwtProperties properties(String secret, long ttlSeconds) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setIssuer(ISSUER);
        properties.setAccessTokenTtlSeconds(ttlSeconds);
        properties.setRefreshTokenTtlSeconds(ttlSeconds);
        return properties;
    }
}