package com.finova.gateway.web;

import com.finova.common.security.AuthenticatedUser;
import com.finova.common.web.CorrelationId;
import org.springframework.web.server.ServerWebExchange;

/**
 * Keys the gateway stores on the {@link ServerWebExchange} so the filters and
 * the error handlers can share request state without a servlet request scope.
 */
public final class ExchangeContext {

    /** Request correlation id, echoing {@link CorrelationId#HEADER}. */
    public static final String CORRELATION_ID = ExchangeContext.class.getName() + ".correlationId";

    /** The caller resolved from a valid access token, if any. */
    public static final String AUTHENTICATED_USER = ExchangeContext.class.getName() + ".authenticatedUser";

    private ExchangeContext() {
    }

    public static void putCorrelationId(ServerWebExchange exchange, String correlationId) {
        exchange.getAttributes().put(CORRELATION_ID, correlationId);
    }

    public static String correlationId(ServerWebExchange exchange) {
        Object value = exchange.getAttributes().get(CORRELATION_ID);
        if (value == null) {
            return "unknown";
        }
        return value.toString();
    }

    public static void putAuthenticatedUser(ServerWebExchange exchange, AuthenticatedUser user) {
        exchange.getAttributes().put(AUTHENTICATED_USER, user);
    }

    public static AuthenticatedUser authenticatedUser(ServerWebExchange exchange) {
        Object value = exchange.getAttributes().get(AUTHENTICATED_USER);
        if (value instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }
}
