package com.finova.gateway.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.common.error.ApiError;
import com.finova.common.error.ErrorCode;
import com.finova.gateway.web.ExchangeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

/**
 * Renders the platform {@link ApiError} envelope from a WebFlux exchange. Every
 * rejection produced by the gateway (authentication, authorisation, rate limit)
 * and every unhandled exception goes through this single writer so the response
 * shape is identical no matter which layer failed.
 */
public class GatewayErrorResponder {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorResponder.class);

    private final ObjectMapper objectMapper;

    public GatewayErrorResponder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatusCode status, ErrorCode code) {
        return write(exchange, status, code, code.defaultMessage(), null);
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatusCode status, ErrorCode code,
                            String message, Map<String, String> headers) {
        ApiError body = ApiError.of(status.value(), code, message, path(exchange),
                ExchangeContext.correlationId(exchange), null);
        return write(exchange, status, body, headers);
    }

    public Mono<Void> write(ServerWebExchange exchange, int status, ErrorCode code) {
        return write(exchange, HttpStatusCode.valueOf(status), code);
    }

    /**
     * Writes an envelope whose code is not part of the canonical
     * {@link ErrorCode} set, such as a gateway-only route or connection failure.
     */
    public Mono<Void> write(ServerWebExchange exchange, HttpStatusCode status, String code,
                            String message, Map<String, String> headers) {
        ApiError body = new ApiError(Instant.now(), status.value(), code, message,
                path(exchange), ExchangeContext.correlationId(exchange), null);
        return write(exchange, status, body, headers);
    }

    private Mono<Void> write(ServerWebExchange exchange, HttpStatusCode status, ApiError body,
                             Map<String, String> headers) {
        byte[] payload = serialise(body);
        if (payload == null) {
            payload = fallbackPayload(status, path(exchange));
        }
        log.debug("Rejecting {} {} with code={} status={}", exchange.getRequest().getMethod(),
                path(exchange), body.code(), status.value());
        if (headers != null) {
            headers.forEach((name, value) -> exchange.getResponse().getHeaders().set(name, value));
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(payload);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private byte[] serialise(ApiError body) {
        try {
            return objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException ex) {
            log.error("Could not serialise the gateway error envelope", ex);
            return null;
        }
    }

    private byte[] fallbackPayload(HttpStatusCode status, String path) {
        String json = "{\"status\":" + status.value() + ",\"code\":\"INTERNAL_ERROR\",\"path\":\"" + path + "\"}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private String path(ServerWebExchange exchange) {
        return exchange.getRequest().getPath().pathWithinApplication().value();
    }
}
