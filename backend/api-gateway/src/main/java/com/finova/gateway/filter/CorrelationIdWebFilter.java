package com.finova.gateway.filter;

import com.finova.common.web.CorrelationId;
import com.finova.gateway.web.ExchangeContext;
import com.finova.gateway.web.RequestHeaders;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Establishes the correlation id for every call: it reuses the id sent by the
 * caller or mints one, publishes it to the logging MDC, echoes it in the
 * response and forwards it to the downstream service so a single id ties the
 * gateway, the services and the Kafka events of one user action together.
 * <p>
 * This runs as the outermost {@link WebFilter} rather than as a Gateway
 * {@code GlobalFilter} so that locally handled endpoints (actuator, the
 * aggregated OpenAPI document, Swagger UI) carry the id in their response too;
 * a {@code GlobalFilter} would only ever see proxied traffic.
 */
@Component
public class CorrelationIdWebFilter implements WebFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(CorrelationId.HEADER);
        String correlationId = incoming == null || incoming.isBlank()
                ? UUID.randomUUID().toString()
                : incoming.trim();

        ExchangeContext.putCorrelationId(exchange, correlationId);
        exchange.getAttributes().put(CorrelationId.REQUEST_ATTRIBUTE, correlationId);
        exchange.getResponse().getHeaders().set(CorrelationId.HEADER, correlationId);
        MDC.put(CorrelationId.MDC_KEY, correlationId);

        ServerHttpRequest forwardedRequest = RequestHeaders.with(exchange.getRequest(),
                headers -> headers.set(CorrelationId.HEADER, correlationId));

        return chain.filter(exchange.mutate().request(forwardedRequest).build())
                .doFinally(signalType -> MDC.remove(CorrelationId.MDC_KEY));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
