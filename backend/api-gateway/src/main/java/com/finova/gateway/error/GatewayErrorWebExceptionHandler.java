package com.finova.gateway.error;

import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.reactive.resource.NoResourceFoundException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Renders every failure that escapes a gateway filter as the platform
 * {@code ApiError} envelope, so a caller never receives a Netty error page or a
 * bare {@code 500}. It runs ahead of the Boot default handler and only speaks
 * when nothing has been written to the response yet.
 */
@Component
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorWebExceptionHandler.class);

    /**
     * Gateway-only codes that are not part of the canonical {@link ErrorCode}
     * set. They are rendered verbatim as the envelope {@code code} so the SPA
     * can branch on them.
     */
    private static final String CODE_NOT_FOUND = "NOT_FOUND";
    private static final String CODE_GATEWAY_ERROR = "GATEWAY_ERROR";

    private static final Map<Integer, String> CODES_BY_STATUS = Map.of(
            400, ErrorCode.VALIDATION_ERROR.name(),
            401, ErrorCode.UNAUTHENTICATED.name(),
            403, ErrorCode.ACCESS_DENIED.name(),
            404, CODE_NOT_FOUND,
            405, ErrorCode.OPERATION_NOT_ALLOWED.name(),
            429, ErrorCode.RATE_LIMIT_EXCEEDED.name(),
            500, ErrorCode.INTERNAL_ERROR.name(),
            502, CODE_GATEWAY_ERROR,
            503, ErrorCode.SERVICE_UNAVAILABLE.name());

    private static final String ROUTE_NOT_FOUND_MESSAGE =
            "No gateway route matches this path. It is unknown to the platform.";
    private static final String NO_INSTANCE_MESSAGE =
            "The requested service is currently unavailable because no healthy instance is registered. "
                    + "Please retry shortly.";

    private final GatewayErrorResponder errorResponder;

    public GatewayErrorWebExceptionHandler(GatewayErrorResponder errorResponder) {
        this.errorResponder = errorResponder;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        Failure failure = classify(ex);
        if (failure.status().is5xxServerError()) {
            log.error("Gateway failure path={} status={} code={}", exchange.getRequest().getPath(),
                    failure.status().value(), failure.code(), ex);
        } else {
            log.debug("Gateway rejected path={} status={} code={}", exchange.getRequest().getPath(),
                    failure.status().value(), failure.code());
        }
        ErrorCode canonical = canonicalCode(failure.code());
        if (canonical != null) {
            return errorResponder.write(exchange, failure.status(), canonical, failure.message(), null);
        }
        return errorResponder.write(exchange, failure.status(), failure.code(), failure.message(), null);
    }

    private Failure classify(Throwable ex) {
        if (ex instanceof BusinessException business) {
            ErrorCode code = business.getErrorCode();
            return failure(statusOf(code.httpStatus()), code.name(), message(business.getMessage(), code));
        }
        if (ex instanceof NoResourceFoundException) {
            return failure(HttpStatus.NOT_FOUND, CODE_NOT_FOUND, ROUTE_NOT_FOUND_MESSAGE);
        }
        if (ex instanceof org.springframework.cloud.gateway.support.NotFoundException notFound) {
            // The gateway reuses this type for two failures: no route matched
            // (404) and no healthy instance of the target service (503, unless
            // spring.cloud.gateway.loadbalancer.use404 flips it to 404).
            return notFound.getStatusCode().value() == HttpStatus.SERVICE_UNAVAILABLE.value()
                    ? failure(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE.name(),
                    NO_INSTANCE_MESSAGE)
                    : failure(HttpStatus.NOT_FOUND, CODE_NOT_FOUND, ROUTE_NOT_FOUND_MESSAGE);
        }
        if (ex instanceof ServerWebInputException) {
            return failure(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.name(),
                    ErrorCode.VALIDATION_ERROR.defaultMessage());
        }
        if (ex instanceof DataBufferLimitException) {
            return failure(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.VALIDATION_ERROR.name(),
                    "The request payload is too large.");
        }
        if (ex instanceof ErrorResponseException errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            if (status.value() == 503) {
                return failure(status, ErrorCode.SERVICE_UNAVAILABLE.name(), NO_INSTANCE_MESSAGE);
            }
            return failure(status, codeFor(status), reasonPhrase(status));
        }
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR.name(),
                ErrorCode.INTERNAL_ERROR.defaultMessage());
    }

    private Failure failure(HttpStatusCode status, String code, String message) {
        return new Failure(status, code, message);
    }

    private HttpStatusCode statusOf(int status) {
        HttpStatus resolved = HttpStatus.resolve(status);
        return resolved == null ? HttpStatus.INTERNAL_SERVER_ERROR : resolved;
    }

    private String codeFor(HttpStatusCode status) {
        return CODES_BY_STATUS.getOrDefault(status.value(),
                status.is5xxServerError() ? CODE_GATEWAY_ERROR : ErrorCode.VALIDATION_ERROR.name());
    }

    private String reasonPhrase(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        if (resolved == null || !CODES_BY_STATUS.containsKey(status.value())) {
            return status.is5xxServerError()
                    ? ErrorCode.INTERNAL_ERROR.defaultMessage()
                    : ErrorCode.VALIDATION_ERROR.defaultMessage();
        }
        return resolved.getReasonPhrase();
    }

    private String message(String candidate, ErrorCode code) {
        return candidate == null ? code.defaultMessage() : candidate;
    }

    private ErrorCode canonicalCode(String code) {
        for (ErrorCode candidate : ErrorCode.values()) {
            if (candidate.name().equals(code)) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private record Failure(HttpStatusCode status, String code, String message) {
    }
}
