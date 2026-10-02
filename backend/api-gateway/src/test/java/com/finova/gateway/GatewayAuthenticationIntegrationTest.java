package com.finova.gateway;

import com.finova.common.security.AuthenticatedUser;
import com.finova.common.security.JwtTokenProvider;
import com.finova.common.web.CorrelationId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * End to end checks against a running gateway with no Eureka and no downstream
 * service: they prove which requests the gateway itself refuses (401 / 403) and
 * which ones it hands over to the routing layer, because only a 503 or a 404 can
 * come back from there.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "finova.gateway.rate-limit.capacity=1000",
                "finova.gateway.rate-limit.auth-capacity=1000"})
class GatewayAuthenticationIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void shouldRejectProtectedRouteWithoutToken() {
        webTestClient.get().uri("/api/accounts")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists(HttpHeaders.WWW_AUTHENTICATE)
                .expectHeader().exists(CorrelationId.HEADER)
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.path").isEqualTo("/api/accounts")
                .jsonPath("$.correlationId").exists();
    }

    @Test
    void shouldRejectProtectedRouteWithAnInvalidToken() {
        webTestClient.get().uri("/api/accounts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("TOKEN_INVALID");
    }

    @Test
    void shouldLetAnAuthenticatedRequestReachTheRoutingLayer() {
        webTestClient.get().uri("/api/accounts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    void shouldRejectANonAdminCallerOnAnAdminRoute() {
        webTestClient.get().uri("/api/fraud/alerts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ACCESS_DENIED");
    }

    @Test
    void shouldReturnNotFoundForAnUnknownRoute() {
        webTestClient.get().uri("/api/there-is-no-such-thing")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("NOT_FOUND")
                .jsonPath("$.correlationId").exists();
    }

    @Test
    void shouldExposeTheAggregatedOpenApiDocument() {
        webTestClient.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.info.title").isEqualTo("Finova API Gateway")
                .jsonPath("$.components.securitySchemes.bearerAuth.scheme").isEqualTo("bearer")
                .jsonPath("$.paths['/api/accounts/**']").exists()
                .jsonPath("$.paths['/api/transactions/**']").exists()
                .jsonPath("$.paths['/api/fraud/**']").exists();
    }

    @Test
    void shouldPointSwaggerUiAtTheAggregatedDocument() {
        webTestClient.get().uri("/v3/api-docs/internal/swagger-config")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.url").isEqualTo("/v3/api-docs");
    }

    @Test
    void shouldExposeTheRouteTable() {
        webTestClient.get().uri("/v3/api-docs/routes")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].target").exists();
    }

    @Test
    void shouldReportHealth() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(CorrelationId.HEADER)
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    private String customerToken() {
        return jwtTokenProvider.createAccessToken("user-42", "user-42@finova.dev",
                AuthenticatedUser.ROLE_CUSTOMER);
    }
}
