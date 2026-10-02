package com.finova.gateway.filter;

import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.common.security.AuthenticatedUser;
import com.finova.gateway.error.GatewayErrorResponder;
import com.finova.gateway.security.GatewayTokenVerifier;
import com.finova.gateway.security.IdentityHeaders;
import com.finova.gateway.security.PathAccessPolicy;
import com.finova.gateway.web.ExchangeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * The gateway authentication and coarse authorisation boundary.
 * <p>
 * A protected route needs a valid access token signed by the user-service. The
 * resolved caller is stored on the exchange and written to the proxied request
 * as {@code X-User-Id} / {@code X-User-E-mail} / {@code X-User-Role}, which is
 * how downstream services learn who the caller is without re-verifying the
 * token. Client supplied {@code X-User-*} headers are always removed first.
 * <p>
 * Paths listed in {@code finova.gateway.admin-paths} additionally require the
 * ADMIN role, and so does any path containing an {@code admin} segment: an
 * {@code /api/.../admin/...} route is an administrative route wherever it
 * appears.
 */
@Component
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationGlobalFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String WWW_AUTHENTICATE = "Bearer realm=\"finova\"";

    private final GatewayTokenVerifier tokenVerifier;
    private final PathAccessPolicy pathAccessPolicy;
    private final IdentityHeaders identityHeaders;
    private final GatewayErrorResponder errorResponder;

    public AuthenticationGlobalFilter(GatewayTokenVerifier tokenVerifier,
                                     PathAccessPolicy pathAccessPolicy,
                                     IdentityHeaders identityHeaders,
                                     GatewayErrorResponder errorResponder) {
        this.tokenVerifier = tokenVerifier;
        this.pathAccessPolicy = pathAccessPolicy;
        this.identityHeaders = identityHeaders;
        this.errorResponder = errorResponder;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (CorsUtils.isPreFlightRequest(request) || pathAccessPolicy.isPublicRequest(request)) {
            return chain.filter(withoutSpoofedIdentity(exchange));
        }
        return verify(request, exchange)
                .onErrorResume(BusinessException.class, ex -> {
                    log.debug("Rejecting {} {}: code={}", request.getMethod(), request.getPath(), ex.getErrorCode());
                    return reject(exchange, ex.getErrorCode(), ex.getMessage()).then(Mono.empty());
                })
                .flatMap(user -> forwardAuthenticated(exchange, chain, user));
    }

    private Mono<AuthenticatedUser> verify(ServerHttpRequest request, ServerWebExchange exchange) {
        String correlationId = ExchangeContext.correlationId(exchange);
        return Mono.fromCallable(() -> tokenVerifier.verify(extractToken(request), correlationId));
    }

    private Mono<Void> forwardAuthenticated(ServerWebExchange exchange, GatewayFilterChain chain,
                                            AuthenticatedUser user) {
        if (pathAccessPolicy.requiresAdminRequest(exchange.getRequest()) && !user.isAdmin()) {
            log.debug("Rejecting non-admin caller {} on admin path {}", user.userId(),
                    exchange.getRequest().getPath().pathWithinApplication().value());
            return errorResponder.write(exchange, HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
                    "Administrator privileges are required for this resource.", null);
        }
        ExchangeContext.putAuthenticatedUser(exchange, user);
        ServerHttpRequest proxiedRequest = identityHeaders.withIdentity(exchange.getRequest(), user);
        return chain.filter(exchange.mutate().request(proxiedRequest).build());
    }

    private ServerWebExchange withoutSpoofedIdentity(ServerWebExchange exchange) {
        return exchange.mutate()
                .request(identityHeaders.withoutIdentity(exchange.getRequest()))
                .build();
    }

    private Mono<Void> reject(ServerWebExchange exchange, ErrorCode code, String message) {
        Map<String, String> headers = code == ErrorCode.UNAUTHENTICATED
                ? Map.of(HttpHeaders.WWW_AUTHENTICATE, WWW_AUTHENTICATE)
                : null;
        return errorResponder.write(exchange, HttpStatus.UNAUTHORIZED, code,
                message == null ? code.defaultMessage() : message, headers);
    }

    private String extractToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX) || header.length() == BEARER_PREFIX.length()) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return header.substring(BEARER_PREFIX.length()).trim();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 300;
    }
}
