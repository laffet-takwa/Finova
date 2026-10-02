package com.finova.gateway.filter;

import com.finova.common.security.AuthenticatedUser;
import com.finova.gateway.config.RateLimitProperties;
import com.finova.gateway.error.GatewayErrorResponder;
import com.finova.gateway.ratelimit.TokenBucketRateLimiter;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The limiter is the platform's only brake on a runaway client, so the contract
 * it must honour is exact: the first N requests of a window pass, request N+1 is
 * refused with {@code 429 RATE_LIMIT_EXCEEDED} and a {@code Retry-After}, and
 * the login route gets its own much smaller budget.
 */
class RateLimitFilterTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String RATE_LIMIT_CODE = "RATE_LIMIT_EXCEEDED";

    private RateLimitProperties properties;
    private TokenBucketRateLimiter rateLimiter;
    private RateLimitGlobalFilter filter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setCapacity(3);
        properties.setWindowSeconds(60L);
        properties.setAuthCapacity(2);
        properties.setAuthPaths(List.of("/api/auth/login"));
        rateLimiter = new TokenBucketRateLimiter(properties);
        filter = new RateLimitGlobalFilter(rateLimiter, properties,
                new GatewayErrorResponder(TestTokens.objectMapper()));
    }

    @Test
    @DisplayName("the request over the ceiling is refused with 429 and a Retry-After header")
    void shouldRejectRequestOverTheCeiling() {
        for (int attempt = 1; attempt <= properties.getCapacity(); attempt++) {
            MockServerWebExchange allowed = GatewayTestExchanges.get("http://localhost:8080/api/accounts");
            CapturingFilterChain allowedChain = new CapturingFilterChain();
            filter.filter(allowed, allowedChain).block(TIMEOUT);
            assertThat(allowedChain.invoked()).as("request %s of %s", attempt, properties.getCapacity()).isTrue();
        }

        MockServerWebExchange rejected = GatewayTestExchanges.get("http://localhost:8080/api/accounts");
        CapturingFilterChain rejectedChain = new CapturingFilterChain();
        filter.filter(rejected, rejectedChain).block(TIMEOUT);

        assertThat(GatewayTestExchanges.statusOf(rejected)).isEqualTo(429);
        assertThat(rejectedChain.invoked()).isFalse();
        assertThat(GatewayTestExchanges.errorOf(rejected).code()).isEqualTo(RATE_LIMIT_CODE);
        assertThat(rejected.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotBlank();
        assertThat(Long.parseLong(rejected.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)))
                .isPositive();
    }

    @Test
    @DisplayName("the limiter counts each caller separately")
    void shouldCountCallersSeparately() {
        for (int attempt = 0; attempt < properties.getCapacity(); attempt++) {
            filter.filter(anonymous("10.0.0.1"), new CapturingFilterChain()).block(TIMEOUT);
        }
        MockServerWebExchange firstClient = anonymous("10.0.0.1");
        filter.filter(firstClient, new CapturingFilterChain()).block(TIMEOUT);
        assertThat(GatewayTestExchanges.statusOf(firstClient)).isEqualTo(429);

        CapturingFilterChain otherClient = new CapturingFilterChain();
        filter.filter(anonymous("10.0.0.2"), otherClient).block(TIMEOUT);
        assertThat(otherClient.invoked()).isTrue();
    }

    @Test
    @DisplayName("an authenticated caller is limited per user id, not per ip")
    void shouldKeyAuthenticatedCallsOnUserId() {
        for (int attempt = 0; attempt < properties.getCapacity(); attempt++) {
            filter.filter(authenticated("user-42"), new CapturingFilterChain()).block(TIMEOUT);
        }
        MockServerWebExchange exceeded = authenticated("user-42");
        filter.filter(exceeded, new CapturingFilterChain()).block(TIMEOUT);
        assertThat(GatewayTestExchanges.statusOf(exceeded)).isEqualTo(429);

        CapturingFilterChain otherUser = new CapturingFilterChain();
        filter.filter(authenticated("user-7"), otherUser).block(TIMEOUT);
        assertThat(otherUser.invoked()).isTrue();
    }

    @Test
    @DisplayName("the login route uses the smaller auth budget")
    void shouldApplyStricterLimitOnAuthRoutes() {
        for (int attempt = 0; attempt < properties.getAuthCapacity(); attempt++) {
            filter.filter(login(), new CapturingFilterChain()).block(TIMEOUT);
        }
        MockServerWebExchange exceeded = login();
        filter.filter(exceeded, new CapturingFilterChain()).block(TIMEOUT);
        assertThat(GatewayTestExchanges.statusOf(exceeded)).isEqualTo(429);
        assertThat(GatewayTestExchanges.errorOf(exceeded).code()).isEqualTo(RATE_LIMIT_CODE);
    }

    @Test
    @DisplayName("Retry-After reflects the time left in the window")
    void shouldReportRetryAfterFromTheWindow() {
        for (int attempt = 0; attempt < 4; attempt++) {
            assertThat(rateLimiter.tryAcquire("burst", 4, 60_000L).allowed()).isTrue();
        }
        TokenBucketRateLimiter.Decision refused = rateLimiter.tryAcquire("burst", 4, 60_000L);
        assertThat(refused.allowed()).isFalse();
        assertThat(refused.retryAfterSeconds()).isEqualTo(15L);
    }

    @Test
    @DisplayName("a fresh client starts with a full bucket")
    void shouldGiveANewClientAFullBucket() {
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThat(rateLimiter.tryAcquire("first", 3, 60_000L).allowed()).isTrue();
        }
        assertThat(rateLimiter.tryAcquire("first", 3, 60_000L).allowed()).isFalse();
        assertThat(rateLimiter.tryAcquire("second", 3, 60_000L).allowed()).isTrue();
    }

    @Test
    @DisplayName("the cleanup task drops idle buckets and keeps active ones")
    void shouldEvictIdleBuckets() {
        rateLimiter.tryAcquire("idle-client", 5, 60_000L);
        assertThat(rateLimiter.bucketCount()).isEqualTo(1);

        rateLimiter.evictIdleBuckets(86_400_000L);
        assertThat(rateLimiter.bucketCount()).isEqualTo(1);

        rateLimiter.evictIdleBuckets(-1_000_000_000L);
        assertThat(rateLimiter.bucketCount()).isZero();
    }

    @Test
    @DisplayName("the limiter is bypassed when disabled")
    void shouldNotLimitWhenDisabled() {
        properties.setEnabled(false);
        for (int attempt = 0; attempt < properties.getCapacity() + 5; attempt++) {
            CapturingFilterChain chain = new CapturingFilterChain();
            filter.filter(anonymous("10.0.0.1"), chain).block(TIMEOUT);
            assertThat(chain.invoked()).isTrue();
        }
    }

    private MockServerWebExchange login() {
        return GatewayTestExchanges.post("http://localhost:8080/api/auth/login");
    }

    private MockServerWebExchange anonymous(String clientIp) {
        return GatewayTestExchanges.get("http://localhost:8080/api/accounts", "X-Forwarded-For", clientIp);
    }

    private MockServerWebExchange authenticated(String userId) {
        MockServerWebExchange exchange = GatewayTestExchanges.get("http://localhost:8080/api/accounts",
                HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.accessToken(userId, "CUSTOMER"));
        ExchangeContext.putAuthenticatedUser(exchange, new AuthenticatedUser(userId,
                userId + "@finova.dev", AuthenticatedUser.ROLE_CUSTOMER, "test-correlation-id"));
        return exchange;
    }
}
