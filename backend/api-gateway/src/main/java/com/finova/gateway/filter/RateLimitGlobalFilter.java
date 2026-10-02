package com.finova.gateway.filter;

import com.finova.common.error.ErrorCode;
import com.finova.common.security.AuthenticatedUser;
import com.finova.gateway.config.RateLimitProperties;
import com.finova.gateway.error.GatewayErrorResponder;
import com.finova.gateway.ratelimit.TokenBucketRateLimiter;
import com.finova.gateway.web.ExchangeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

/**
 * In-memory token bucket applied per caller: the user id when the request is
 * authenticated, the client ip otherwise. Requests over the ceiling are
 * rejected with {@code 429 RATE_LIMIT_EXCEEDED} and a {@code Retry-After}
 * header, so the SPA can back off instead of hammering the platform.
 * <p>
 * The auth routes in {@code finova.gateway.rate-limit.auth-paths} (login by
 * default) use the much smaller {@code auth-capacity} to slow credential
 * stuffing; the limiter is deliberately Redis-free so the gateway has no
 * external dependency.
 */
@Component
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitGlobalFilter.class);
    private static final String API_BUCKET = "api:";
    private static final String AUTH_BUCKET = "auth:";

    private final TokenBucketRateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final GatewayErrorResponder errorResponder;
    private final List<String> authPathPrefixes;

    public RateLimitGlobalFilter(TokenBucketRateLimiter rateLimiter,
                                 RateLimitProperties properties,
                                 GatewayErrorResponder errorResponder) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.errorResponder = errorResponder;
        this.authPathPrefixes = properties.getAuthPaths().stream()
                .map(String::trim)
                .filter(path -> !path.isEmpty())
                .toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        boolean authRoute = isAuthRoute(path);
        int capacity = authRoute ? properties.getAuthCapacity() : properties.getCapacity();
        long windowMillis = properties.getWindowSeconds() * 1000L;
        String key = (authRoute ? AUTH_BUCKET : API_BUCKET) + clientKey(exchange);

        TokenBucketRateLimiter.Decision decision = rateLimiter.tryAcquire(key, capacity, windowMillis);
        if (decision.allowed()) {
            return chain.filter(exchange);
        }
        long retryAfterSeconds = decision.retryAfterSeconds();
        log.info("Rate limit exceeded path={} client={} scope={} retryAfterSeconds={}",
                path, clientKey(exchange), authRoute ? "auth" : "api", retryAfterSeconds);
        Map<String, String> headers = Map.of(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        return errorResponder.write(exchange, HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMIT_EXCEEDED,
                ErrorCode.RATE_LIMIT_EXCEEDED.defaultMessage(), headers);
    }

    private boolean isAuthRoute(String path) {
        for (String prefix : authPathPrefixes) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    private String clientKey(ServerWebExchange exchange) {
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

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 400;
    }
}
