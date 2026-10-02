package com.finova.gateway.filter;

import com.finova.common.error.ApiError;
import com.finova.common.error.ErrorCode;
import com.finova.common.security.AuthenticatedUser;
import com.finova.gateway.config.GatewayProperties;
import com.finova.gateway.error.GatewayErrorResponder;
import com.finova.gateway.security.GatewayTokenVerifier;
import com.finova.gateway.security.IdentityHeaders;
import com.finova.gateway.security.PathAccessPolicy;
import com.finova.gateway.support.CapturingFilterChain;
import com.finova.gateway.support.GatewayTestExchanges;
import com.finova.gateway.support.TestTokens;
import com.finova.gateway.web.ExchangeContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gateway is the only component that decides who the caller is, so these
 * tests pin the three properties downstream services depend on: an anonymous or
 * tampered request is rejected with the platform error envelope, identity is
 * taken from the token, and a client can never spoof it with a header.
 */
class JwtValidationFilterTest {

    private static final String ADMIN_TOKEN = TestTokens.accessToken("admin-1", AuthenticatedUser.ROLE_ADMIN);
    private static final String CUSTOMER_TOKEN = TestTokens.accessToken("user-42", AuthenticatedUser.ROLE_CUSTOMER);

    private AuthenticationGlobalFilter filter;
    private GatewayProperties properties;

    @BeforeEach
    void setUp() {
        properties = new GatewayProperties();
        filter = new AuthenticationGlobalFilter(
                new GatewayTokenVerifier(TestTokens.properties(), TestTokens.objectMapper()),
                new PathAccessPolicy(properties),
                new IdentityHeaders(),
                new GatewayErrorResponder(TestTokens.objectMapper()));
    }

    @Test
    @DisplayName("an unauthenticated request to a protected route is rejected with the ApiError envelope")
    void shouldRejectRequestWithoutToken() {
        MockServerWebExchange exchange = GatewayTestExchanges.get("http://localhost:8080/api/accounts");
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        ApiError error = GatewayTestExchanges.errorOf(exchange);
        assertThat(GatewayTestExchanges.statusOf(exchange)).isEqualTo(401);
        assertThat(error.code()).isEqualTo(ErrorCode.UNAUTHENTICATED.name());
        assertThat(error.status()).isEqualTo(401);
        assertThat(error.path()).isEqualTo("/api/accounts");
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isNotBlank();
        assertThat(chain.invoked()).isFalse();
    }

    @Test
    @DisplayName("a malformed Authorization header is treated as a missing token")
    void shouldRejectRequestWithMalformedAuthorizationHeader() {
        MockServerWebExchange exchange = GatewayTestExchanges.get("http://localhost:8080/api/accounts",
                HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        assertThat(GatewayTestExchanges.errorOf(exchange).code()).isEqualTo(ErrorCode.UNAUTHENTICATED.name());
        assertThat(chain.invoked()).isFalse();
    }

    @Test
    @DisplayName("an expired token is reported as TOKEN_EXPIRED so the SPA can refresh it")
    void shouldRejectExpiredToken() {
        MockServerWebExchange exchange = GatewayTestExchanges.get("http://localhost:8080/api/accounts",
                HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.expiredAccessToken("user-42", "CUSTOMER"));
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        assertThat(GatewayTestExchanges.statusOf(exchange)).isEqualTo(401);
        assertThat(GatewayTestExchanges.errorOf(exchange).code()).isEqualTo(ErrorCode.TOKEN_EXPIRED.name());
    }

    @Test
    @DisplayName("a token signed with a foreign secret is rejected as TOKEN_INVALID")
    void shouldRejectTokenSignedWithAnotherSecret() {
        MockServerWebExchange exchange = GatewayTestExchanges.get("http://localhost:8080/api/accounts",
                HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.foreignAccessToken("user-42", "ADMIN"));
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        assertThat(GatewayTestExchanges.statusOf(exchange)).isEqualTo(401);
        assertThat(GatewayTestExchanges.errorOf(exchange).code()).isEqualTo(ErrorCode.TOKEN_INVALID.name());
    }

    @Test
    @DisplayName("a refresh token cannot be used as an access token")
    void shouldRejectRefreshTokenUsedAsAccessToken() {
        MockServerWebExchange exchange = GatewayTestExchanges.get("http://localhost:8080/api/accounts",
                HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.refreshToken("user-42", "CUSTOMER"));
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        assertThat(GatewayTestExchanges.statusOf(exchange)).isEqualTo(401);
        assertThat(GatewayTestExchanges.errorOf(exchange).code()).isEqualTo(ErrorCode.TOKEN_INVALID.name());
    }

    @Test
    @DisplayName("a valid token is forwarded with the caller identity in the X-User-* headers")
    void shouldPropagateIdentityDownstream() {
        MockServerWebExchange exchange = GatewayTestExchanges.get("http://localhost:8080/api/accounts/acc-1",
                HttpHeaders.AUTHORIZATION, "Bearer " + CUSTOMER_TOKEN);
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        assertThat(chain.invoked()).isTrue();
        HttpHeaders forwarded = chain.forwarded().getRequest().getHeaders();
        assertThat(forwarded.getFirst(IdentityHeaders.USER_ID)).isEqualTo("user-42");
        assertThat(forwarded.getFirst(IdentityHeaders.USER_EMAIL)).isEqualTo("user-42@finova.dev");
        assertThat(forwarded.getFirst(IdentityHeaders.USER_ROLE)).isEqualTo(AuthenticatedUser.ROLE_CUSTOMER);
        AuthenticatedUser user = ExchangeContext.authenticatedUser(chain.forwarded());
        assertThat(user).isNotNull();
        assertThat(user.userId()).isEqualTo("user-42");
        assertThat(user.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("a client supplied X-User-Id header is overwritten by the verified identity")
    void shouldOverwriteClientSuppliedIdentityHeader() {
        MockServerWebExchange exchange = GatewayTestExchanges.get("http://localhost:8080/api/accounts",
                HttpHeaders.AUTHORIZATION, "Bearer " + CUSTOMER_TOKEN,
                IdentityHeaders.USER_ID, "attacker",
                IdentityHeaders.USER_ROLE, AuthenticatedUser.ROLE_ADMIN);
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        HttpHeaders forwarded = chain.forwarded().getRequest().getHeaders();
        assertThat(forwarded.get(IdentityHeaders.USER_ID)).containsExactly("user-42");
        assertThat(forwarded.get(IdentityHeaders.USER_ROLE)).containsExactly(AuthenticatedUser.ROLE_CUSTOMER);
    }

    @Test
    @DisplayName("a client supplied X-User-* header is stripped from a public request")
    void shouldStripClientSuppliedIdentityHeaderOnPublicRoute() {
        MockServerWebExchange exchange = GatewayTestExchanges.get("http://localhost:8080/api/auth/login",
                IdentityHeaders.USER_ID, "attacker",
                "X-User-Custom", "anything");
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        assertThat(chain.invoked()).isTrue();
        assertThat(chain.forwarded().getRequest().getHeaders().getFirst(IdentityHeaders.USER_ID)).isNull();
        assertThat(chain.forwarded().getRequest().getHeaders().getFirst("X-User-Custom")).isNull();
    }

    @Test
    @DisplayName("a non admin caller is rejected on an admin path")
    void shouldRejectNonAdminOnAdminPath() {
        MockServerWebExchange exchange = GatewayTestExchanges.get("http://localhost:8080/api/fraud/alerts",
                HttpHeaders.AUTHORIZATION, "Bearer " + CUSTOMER_TOKEN);
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        assertThat(GatewayTestExchanges.statusOf(exchange)).isEqualTo(403);
        assertThat(GatewayTestExchanges.errorOf(exchange).code()).isEqualTo(ErrorCode.ACCESS_DENIED.name());
        assertThat(chain.invoked()).isFalse();
    }

    @Test
    @DisplayName("an admin caller reaches an admin path and an /admin/ sub resource is admin only")
    void shouldAllowAdminOnAdminPath() {
        MockServerWebExchange configuredPath = GatewayTestExchanges.get("http://localhost:8080/api/fraud/alerts",
                HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN);
        CapturingFilterChain configuredChain = new CapturingFilterChain();
        filter.filter(configuredPath, configuredChain).block(Duration.ofSeconds(5));
        assertThat(configuredChain.invoked()).isTrue();

        MockServerWebExchange adminSegment = GatewayTestExchanges.get("http://localhost:8080/api/accounts/admin",
                HttpHeaders.AUTHORIZATION, "Bearer " + CUSTOMER_TOKEN);
        CapturingFilterChain adminChain = new CapturingFilterChain();
        filter.filter(adminSegment, adminChain).block(Duration.ofSeconds(5));
        assertThat(GatewayTestExchanges.statusOf(adminSegment)).isEqualTo(403);
        assertThat(adminChain.invoked()).isFalse();
    }
}
