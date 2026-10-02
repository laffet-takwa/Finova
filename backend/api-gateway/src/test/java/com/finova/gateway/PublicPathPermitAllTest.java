package com.finova.gateway;

import com.finova.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Registering, signing in and refreshing must be reachable without a token,
 * otherwise the SPA could never obtain one. These requests are forwarded to the
 * user-service, so the answer here is "no instance available", never 401.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "finova.gateway.rate-limit.capacity=1000",
                "finova.gateway.rate-limit.auth-capacity=1000"})
class PublicPathPermitAllTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("POST /api/auth/login without a token is not blocked by the gateway")
    void shouldNotBlockLoginWithoutToken() {
        webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"ada@finova.dev\",\"password\":\"Passw0rd!\"}")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.code").isEqualTo(ErrorCode.SERVICE_UNAVAILABLE.name());
    }

    @Test
    @DisplayName("POST /api/auth/register without a token is not blocked by the gateway")
    void shouldNotBlockRegisterWithoutToken() {
        webTestClient.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"ada@finova.dev\",\"password\":\"Passw0rd!\"}")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.code").isEqualTo(ErrorCode.SERVICE_UNAVAILABLE.name());
    }

    @Test
    @DisplayName("POST /api/auth/refresh without a token is not blocked by the gateway")
    void shouldNotBlockRefreshWithoutToken() {
        webTestClient.post().uri("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"refreshToken\":\"whatever\"}")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.code").isEqualTo(ErrorCode.SERVICE_UNAVAILABLE.name());
    }

    @Test
    @DisplayName("the actuator and documentation paths stay public")
    void shouldKeepOperationsPathsPublic() {
        webTestClient.get().uri("/actuator/health").exchange().expectStatus().isOk();
        webTestClient.get().uri("/v3/api-docs").exchange().expectStatus().isOk();
        webTestClient.get().uri("/v3/api-docs/internal").exchange().expectStatus().isOk();
        webTestClient.get().uri("/webjars/swagger-ui/index.html")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> assertThat(body).contains("swagger-ui"));
    }

    @Test
    @DisplayName("a protected path is still refused without a token")
    void shouldStillRefuseProtectedPaths() {
        webTestClient.get().uri("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
