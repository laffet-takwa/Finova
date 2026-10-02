package com.finova.gateway.filter;

import com.finova.common.web.CorrelationId;
import com.finova.gateway.support.GatewayTestExchanges;
import com.finova.gateway.web.ExchangeContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A correlation id is the thread that ties a log line, a Kafka event and a
 * support ticket together, so it must be generated when absent, echoed when
 * present and always forwarded to the downstream service.
 */
class CorrelationIdFilterTest {

    private final CorrelationIdWebFilter filter = new CorrelationIdWebFilter();

    @Test
    @DisplayName("a request without the header gets a generated id returned in the response")
    void shouldGenerateCorrelationIdWhenAbsent() {
        MockServerWebExchange exchange = GatewayTestExchanges.get("http://localhost:8080/api/accounts");
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, recordingChain(forwarded)).block();

        String correlationId = exchange.getResponse().getHeaders().getFirst(CorrelationId.HEADER);
        assertThat(correlationId).isNotBlank();
        assertThat(UUID.fromString(correlationId)).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst(CorrelationId.HEADER))
                .isEqualTo(correlationId);
        assertThat(ExchangeContext.correlationId(forwarded.get())).isEqualTo(correlationId);
    }

    @Test
    @DisplayName("the correlation id sent by the caller is echoed and forwarded unchanged")
    void shouldEchoSuppliedCorrelationId() {
        String supplied = "test-correlation-id-1234";
        MockServerWebExchange exchange = GatewayTestExchanges.get("http://localhost:8080/api/accounts",
                CorrelationId.HEADER, supplied);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, recordingChain(forwarded)).block();

        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationId.HEADER)).isEqualTo(supplied);
        assertThat(forwarded.get().getRequest().getHeaders().getFirst(CorrelationId.HEADER)).isEqualTo(supplied);
    }

    @Test
    @DisplayName("a blank correlation id is replaced by a generated one")
    void shouldReplaceBlankCorrelationId() {
        MockServerWebExchange exchange = GatewayTestExchanges.get("http://localhost:8080/api/accounts",
                CorrelationId.HEADER, "   ");
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, recordingChain(forwarded)).block();

        String correlationId = exchange.getResponse().getHeaders().getFirst(CorrelationId.HEADER);
        assertThat(correlationId).isNotBlank().isNotEqualTo("   ");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst(CorrelationId.HEADER))
                .isEqualTo(correlationId);
    }

    @Test
    @DisplayName("the correlation id is published to the MDC and cleared afterwards")
    void shouldPublishCorrelationIdToMdc() {
        MockServerWebExchange exchange = GatewayTestExchanges.get("http://localhost:8080/api/accounts");
        List<String> observed = new ArrayList<>();

        filter.filter(exchange, chainThatRecords(observed)).block();

        assertThat(observed).hasSize(1);
        assertThat(observed.get(0)).isNotBlank();
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    private WebFilterChain recordingChain(AtomicReference<ServerWebExchange> target) {
        return exchange -> {
            target.set(exchange);
            exchange.getResponse().setStatusCode(HttpStatus.OK);
            return exchange.getResponse().setComplete();
        };
    }

    private WebFilterChain chainThatRecords(List<String> sink) {
        return exchange -> {
            sink.add(MDC.get(CorrelationId.MDC_KEY));
            exchange.getResponse().setStatusCode(HttpStatus.OK);
            return exchange.getResponse().setComplete();
        };
    }
}
