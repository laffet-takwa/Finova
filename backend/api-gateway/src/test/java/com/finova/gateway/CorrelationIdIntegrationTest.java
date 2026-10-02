package com.finova.gateway;

import com.finova.common.security.AuthenticatedUser;
import com.finova.common.security.JwtTokenProvider;
import com.finova.common.web.CorrelationId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The correlation id has to be present on every response, including the ones the
 * gateway rejects before routing, and it has to be the id the caller sent when
 * there is one.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "finova.gateway.rate-limit.capacity=1000",
                "finova.gateway.rate-limit.auth-capacity=1000"})
class CorrelationIdIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("a request without the header gets a generated id back in the response")
    void shouldReturnGeneratedCorrelationId() {
        String correlationId = webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .returnResult(Void.class)
                .getResponseHeaders()
                .getFirst(CorrelationId.HEADER);

        assertThat(correlationId).isNotBlank();
        assertThat(UUID.fromString(correlationId)).isNotNull();
    }

    @Test
    @DisplayName("a request with the header gets the same id back")
    void shouldEchoCorrelationId() {
        String supplied = "integration-" + UUID.randomUUID();
        webTestClient.get().uri("/actuator/health")
                .header(CorrelationId.HEADER, supplied)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(CorrelationId.HEADER, supplied);
    }

    @Test
    @DisplayName("a rejected request still carries the correlation id in the envelope")
    void shouldCarryCorrelationIdInTheErrorEnvelope() {
        String supplied = "integration-" + UUID.randomUUID();
        webTestClient.get().uri("/api/accounts")
                .header(CorrelationId.HEADER, supplied)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(CorrelationId.HEADER, supplied)
                .expectBody()
                .jsonPath("$.correlationId").isEqualTo(supplied);
    }

    @Test
    @DisplayName("a proxied request forwards the correlation id downstream")
    void shouldForwardCorrelationIdDownstream() {
        String supplied = "integration-" + UUID.randomUUID();
        webTestClient.get().uri("/api/accounts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .header(CorrelationId.HEADER, supplied)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().valueEquals(CorrelationId.HEADER, supplied);
    }

    private String customerToken() {
        return jwtTokenProvider.createAccessToken("user-42", "user-42@finova.dev",
                AuthenticatedUser.ROLE_CUSTOMER);
    }
}
