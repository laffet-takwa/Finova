package com.finova.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.UUID;

/**
 * The Vue SPA runs on another origin, so the gateway owns CORS: a preflight from
 * an allowed origin must be answered with the right headers, and an unknown
 * origin must be refused.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "finova.gateway.allowed-origins=http://localhost:5173,https://app.finova.dev"})
class CorsConfigurationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String OTHER_ALLOWED_ORIGIN = "https://app.finova.dev";
    private static final String FOREIGN_ORIGIN = "https://evil.example.com";

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("a preflight from an allowed origin is answered with the CORS contract")
    void shouldAnswerPreflightFromAllowedOrigin() {
        webTestClient.options().uri("/api/accounts")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,idempotency-key")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN)
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600")
                .expectHeader().valueMatches(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, ".*POST.*")
                .expectHeader().valueMatches(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "(?i).*authorization.*")
                .expectHeader().valueMatches(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "(?i).*idempotency-key.*")
                .expectHeader().valueMatches(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "(?i).*x-correlation-id.*");
    }

    @Test
    @DisplayName("every configured origin is accepted")
    void shouldAcceptEveryConfiguredOrigin() {
        webTestClient.options().uri("/api/transactions")
                .header(HttpHeaders.ORIGIN, OTHER_ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, OTHER_ALLOWED_ORIGIN);
    }

    @Test
    @DisplayName("a preflight from an unknown origin is refused")
    void shouldRefusePreflightFromForeignOrigin() {
        webTestClient.options().uri("/api/accounts")
                .header(HttpHeaders.ORIGIN, FOREIGN_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("a preflight never needs a token")
    void shouldAnswerPreflightWithoutToken() {
        webTestClient.options().uri("/api/fraud/alerts")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN);
    }

    @Test
    @DisplayName("an actual cross origin call keeps the correlation id in the response")
    void shouldKeepCorrelationIdOnCrossOriginCall() {
        String correlationId = "cors-" + UUID.randomUUID();
        webTestClient.get().uri("/actuator/health")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header("X-Correlation-Id", correlationId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN)
                .expectHeader().valueEquals("X-Correlation-Id", correlationId);
    }
}
