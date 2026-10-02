package com.finova.gateway.filter;

import com.finova.common.security.AuthenticatedUser;
import com.finova.common.web.CorrelationIdFilter;
import com.finova.gateway.web.ExchangeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Emits one access-log line per request in a flat {@code key=value} shape that
 * Logstash grok / dissect can parse, plus the MDC fields every Finova service
 * logs: {@code httpMethod}, {@code requestPath}, {@code responseStatus},
 * {@code durationMs} and {@code userId}.
 * <p>
 * It is the second outermost filter so that the caller identity resolved by the
 * authentication filter is available when the line is written, and so that
 * rejections raised by the security chain (CORS, malformed requests) are logged
 * too.
 */
@Component
public class RequestLoggingWebFilter implements WebFilter, Ordered {

    public static final String MDC_RESPONSE_STATUS = "responseStatus";
    public static final String MDC_DURATION_MS = "durationMs";
    public static final String MDC_USER_ID = "userId";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingWebFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().name();
        String path = request.getPath().pathWithinApplication().value();
        long startedAt = System.nanoTime();

        MDC.put(CorrelationIdFilter.MDC_METHOD, method);
        MDC.put(CorrelationIdFilter.MDC_REQUEST_PATH, path);

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
                    int status = exchange.getResponse().getStatusCode() == null
                            ? 200
                            : exchange.getResponse().getStatusCode().value();
                    String userId = resolveUserId(exchange);
                    MDC.put(MDC_RESPONSE_STATUS, String.valueOf(status));
                    MDC.put(MDC_DURATION_MS, String.valueOf(durationMs));
                    MDC.put(MDC_USER_ID, userId);
                    log.info("http_request method={} path={} status={} durationMs={} userId={}",
                            method, path, status, durationMs, userId);
                    clearMdc();
                });
    }

    private String resolveUserId(ServerWebExchange exchange) {
        AuthenticatedUser user = ExchangeContext.authenticatedUser(exchange);
        if (user != null) {
            return user.userId();
        }
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress address = exchange.getRequest().getRemoteAddress();
        return address == null ? "unknown" : address.getAddress().getHostAddress();
    }

    private void clearMdc() {
        MDC.remove(CorrelationIdFilter.MDC_METHOD);
        MDC.remove(CorrelationIdFilter.MDC_REQUEST_PATH);
        MDC.remove(MDC_RESPONSE_STATUS);
        MDC.remove(MDC_DURATION_MS);
        MDC.remove(MDC_USER_ID);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 50;
    }
}
