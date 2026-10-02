package com.finova.gateway.support;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * A terminal {@link GatewayFilterChain} that records the exchange a filter
 * decided to forward, so a test can assert on the headers the downstream service
 * would receive without starting a second server.
 */
public class CapturingFilterChain implements GatewayFilterChain {

    private ServerWebExchange forwarded;
    private boolean invoked;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange) {
        this.forwarded = exchange;
        this.invoked = true;
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        return exchange.getResponse().setComplete();
    }

    public ServerWebExchange forwarded() {
        return forwarded;
    }

    public boolean invoked() {
        return invoked;
    }
}
