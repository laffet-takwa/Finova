package com.finova.gateway.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.common.error.ApiError;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

/**
 * Builds the {@link MockServerWebExchange} instances the filter unit tests run
 * against and reads back the {@link ApiError} envelope a rejection wrote, so a
 * test asserts on exactly what a client would receive.
 */
public final class GatewayTestExchanges {

    private static final ObjectMapper MAPPER = TestTokens.objectMapper();

    private GatewayTestExchanges() {
    }

    public static MockServerWebExchange get(String uri, String... headerNameValuePairs) {
        MockServerHttpRequest.BaseBuilder<?> builder =
                MockServerHttpRequest.method(HttpMethod.GET, URI.create(uri));
        applyHeaders(builder, headerNameValuePairs);
        return MockServerWebExchange.from(builder.build());
    }

    public static MockServerWebExchange post(String uri, String... headerNameValuePairs) {
        MockServerHttpRequest.BodyBuilder builder = MockServerHttpRequest
                .method(HttpMethod.POST, URI.create(uri))
                .contentType(MediaType.APPLICATION_JSON);
        applyHeaders(builder, headerNameValuePairs);
        return MockServerWebExchange.from(builder.body("{}"));
    }

    public static ApiError errorOf(ServerWebExchange exchange) {
        try {
            String body = ((MockServerHttpResponse) exchange.getResponse())
                    .getBodyAsString().block(Duration.ofSeconds(5));
            JsonNode node = MAPPER.readTree(body);
            return new ApiError(
                    Instant.parse(node.get("timestamp").asText()),
                    node.get("status").asInt(),
                    node.get("code").asText(),
                    node.get("message").asText(),
                    node.get("path").asText(),
                    node.hasNonNull("correlationId") ? node.get("correlationId").asText() : null,
                    null);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Response body is not an ApiError envelope", ex);
        }
    }

    public static int statusOf(ServerWebExchange exchange) {
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        return status == null ? 0 : status.value();
    }

    private static void applyHeaders(MockServerHttpRequest.BaseBuilder<?> builder,
                                     String... headerNameValuePairs) {
        for (int index = 0; index + 1 < headerNameValuePairs.length; index += 2) {
            builder.header(headerNameValuePairs[index], headerNameValuePairs[index + 1]);
        }
    }
}
