package com.finova.gateway;

import com.finova.common.security.AuthenticatedUser;
import com.finova.common.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rate limiter has to stop a runaway client at the edge, which means the
 * refusal is the platform envelope, not a proxy error page, and it tells the
 * caller how long to wait.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "finova.gateway.rate-limit.capacity=3",
                "finova.gateway.rate-limit.window-seconds=60",
                "finova.gateway.rate-limit.auth-capacity=2"})
class RateLimitIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String token;

    @BeforeEach
    void setUp() {
        token = jwtTokenProvider.createAccessToken("rate-limited-user", "rate@finova.dev",
                AuthenticatedUser.ROLE_CUSTOMER);
    }

    @Test
    @DisplayName("the request over the ceiling is refused with 429, the envelope and Retry-After")
    void shouldRefuseRequestOverTheCeiling() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            webTestClient.get().uri("/api/accounts")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .exchange()
                    .expectStatus().isEqualTo(503);
        }

        String retryAfter = webTestClient.get().uri("/api/accounts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectBody()
                .jsonPath("$.code").isEqualTo("RATE_LIMIT_EXCEEDED")
                .jsonPath("$.status").isEqualTo(429)
                .jsonPath("$.correlationId").exists()
                .returnResult()
                .getResponseHeaders()
                .getFirst(HttpHeaders.RETRY_AFTER);

        assertThat(retryAfter).isNotBlank();
        assertThat(Long.parseLong(retryAfter)).isPositive();
    }

    @Test
    @DisplayName("the login route is limited by the smaller auth budget")
    void shouldRefuseRepeatedLogins() {
        webTestClient.post().uri("/api/auth/login").exchange().expectStatus().isEqualTo(503);
        webTestClient.post().uri("/api/auth/login").exchange().expectStatus().isEqualTo(503);

        webTestClient.post().uri("/api/auth/login")
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().exists(HttpHeaders.RETRY_AFTER)
                .expectBody()
                .jsonPath("$.code").isEqualTo("RATE_LIMIT_EXCEEDED");
    }
}
