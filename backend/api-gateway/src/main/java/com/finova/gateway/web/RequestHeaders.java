package com.finova.gateway.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * Rewrites the headers of the request that is about to be proxied.
 * <p>
 * {@code ServerHttpRequest.Builder.headers(...)} exposes a read-only view, so a
 * header can only be added or replaced there, never removed. The gateway has to
 * remove headers (a client supplied {@code X-User-Id}), so the outbound request
 * is built from a fresh mutable copy of the inbound headers instead.
 */
public final class RequestHeaders {

    private RequestHeaders() {
    }

    public static ServerHttpRequest with(ServerHttpRequest request, Consumer<HttpHeaders> customiser) {
        HttpHeaders headers = new HttpHeaders();
        request.getHeaders().forEach((name, values) -> headers.put(name, new ArrayList<>(values)));
        customiser.accept(headers);
        return new ServerHttpRequestDecorator(request) {
            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };
    }
}
