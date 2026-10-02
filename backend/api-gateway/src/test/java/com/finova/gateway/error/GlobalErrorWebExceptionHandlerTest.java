package com.finova.gateway.error;

import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.gateway.support.GatewayTestExchanges;
import com.finova.gateway.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Whatever fails inside the gateway, the caller must receive the same
 * {@code ApiError} envelope the other Finova services return, with a code it can
 * branch on.
 */
class GlobalErrorWebExceptionHandlerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private GatewayErrorWebExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GatewayErrorWebExceptionHandler(
                new GatewayErrorResponder(TestTokens.objectMapper()));
    }

    @Test
    @DisplayName("an unknown route is rendered as a 404 NOT_FOUND envelope")
    void shouldRenderRouteNotFound() {
        MockServerWebExchange exchange = GatewayTestExchanges.get("http://localhost:8080/api/does-not-exist");

        handler.handle(exchange, NotFoundException.create(true, "Unable to find instance")).block(TIMEOUT);

        assertThat(GatewayTestExchanges.statusOf(exchange)).isEqualTo(404);
        assertThat(GatewayTestExchanges.errorOf(exchange).code()).isEqualTo("NOT_FOUND");
        assertThat(GatewayTestExchanges.errorOf(exchange).path()).isEqualTo("/api/does-not-exist");
    }

    @Test
    @DisplayName("a missing service instance is rendered as a retryable 503")
    void shouldRenderServiceUnavailable() {
        MockServerWebExchange exchange = GatewayTestExchanges.get("http://localhost:8080/api/accounts");

        handler.handle(exchange,
                NotFoundException.create(false, "Unable to find instance for account-service")).block(TIMEOUT);

        assertThat(GatewayTestExchanges.statusOf(exchange)).isEqualTo(503);
        assertThat(GatewayTestExchanges.errorOf(exchange).code())
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE.name());
        assertThat(GatewayTestExchanges.errorOf(exchange).message()).contains("retry");
    }

    @Test
    @DisplayName("an unmapped static resource is rendered as a 404 NOT_FOUND envelope")
    void shouldRenderMissingResource() {
        MockServerWebExchange exchange = GatewayTestExchanges.get("http://localhost:8080/nothing/here");

        handler.handle(exchange, new NoResourceFoundException("/nothing/here")).block(TIMEOUT);

        assertThat(GatewayTestExchanges.statusOf(exchange)).isEqualTo(404);
        assertThat(GatewayTestExchanges.errorOf(exchange).code()).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("a method not allowed is rendered as a 405 envelope")
    void shouldRenderMethodNotAllowed() {
        MockServerWebExchange exchange = GatewayTestExchanges.post("http://localhost:8080/api/accounts");

        handler.handle(exchange, new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED)).block(TIMEOUT);

        assertThat(GatewayTestExchanges.statusOf(exchange)).isEqualTo(405);
        assertThat(GatewayTestExchanges.errorOf(exchange).code())
                .isEqualTo(ErrorCode.OPERATION_NOT_ALLOWED.name());
    }

    @Test
    @DisplayName("a malformed request is rendered as a 400 envelope")
    void shouldRenderBadRequest() {
        MockServerWebExchange exchange = GatewayTestExchanges.post("http://localhost:8080/api/transactions");

        handler.handle(exchange, new ServerWebInputException("unreadable body")).block(TIMEOUT);

        assertThat(GatewayTestExchanges.statusOf(exchange)).isEqualTo(400);
        assertThat(GatewayTestExchanges.errorOf(exchange).code())
                .isEqualTo(ErrorCode.VALIDATION_ERROR.name());
    }

    @Test
    @DisplayName("an oversized payload is rendered as a 400 envelope")
    void shouldRenderPayloadTooLarge() {
        MockServerWebExchange exchange = GatewayTestExchanges.post("http://localhost:8080/api/transactions");

        handler.handle(exchange, new DataBufferLimitException("too big")).block(TIMEOUT);

        assertThat(GatewayTestExchanges.statusOf(exchange)).isEqualTo(413);
        assertThat(GatewayTestExchanges.errorOf(exchange).code())
                .isEqualTo(ErrorCode.VALIDATION_ERROR.name());
    }

    @Test
    @DisplayName("an unexpected failure is rendered as a 500 envelope without leaking internals")
    void shouldRenderInternalError() {
        MockServerWebExchange exchange = GatewayTestExchanges.get("http://localhost:8080/api/accounts");

        handler.handle(exchange, new IllegalStateException("connection pool exhausted at 10.0.0.7:5432"))
                .block(TIMEOUT);

        assertThat(GatewayTestExchanges.statusOf(exchange)).isEqualTo(500);
        assertThat(GatewayTestExchanges.errorOf(exchange).code())
                .isEqualTo(ErrorCode.INTERNAL_ERROR.name());
        assertThat(GatewayTestExchanges.errorOf(exchange).message()).doesNotContain("10.0.0.7");
    }

    @Test
    @DisplayName("a business exception keeps its canonical code")
    void shouldRenderBusinessException() {
        MockServerWebExchange exchange = GatewayTestExchanges.get("http://localhost:8080/api/accounts");

        handler.handle(exchange, new BusinessException(ErrorCode.INSUFFICIENT_BALANCE, "Balance too low"))
                .block(TIMEOUT);

        assertThat(GatewayTestExchanges.statusOf(exchange)).isEqualTo(422);
        assertThat(GatewayTestExchanges.errorOf(exchange).code())
                .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE.name());
        assertThat(GatewayTestExchanges.errorOf(exchange).message()).isEqualTo("Balance too low");
    }

    @Test
    @DisplayName("a committed response is left to the caller instead of being rewritten")
    void shouldNotTouchACommittedResponse() {
        ServerWebExchange exchange = committedExchange();
        assertThat(exchange.getResponse().isCommitted()).isTrue();

        Throwable thrown = catchThrowable(
                () -> handler.handle(exchange, new IllegalStateException("too late")).block(TIMEOUT));

        assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessage("too late");
    }

    private ServerWebExchange committedExchange() {
        MockServerWebExchange exchange = GatewayTestExchanges.get("http://localhost:8080/api/accounts");
        byte[] body = "{\"already\":\"written\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body))).block(TIMEOUT);
        return exchange;
    }
}
