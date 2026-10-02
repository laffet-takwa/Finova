package com.finova.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.common.error.ApiError;
import com.finova.common.error.ErrorCode;
import com.finova.common.web.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.time.Instant;

/** Returns the platform error envelope instead of an HTML error page. */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorCode code = ErrorCode.UNAUTHENTICATED;
        response.setStatus(code.httpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String correlationId = request.getHeader(CorrelationId.HEADER);
        objectMapper.writeValue(response.getOutputStream(), ApiError.of(code.httpStatus(), code,
                code.defaultMessage(), request.getRequestURI(), correlationId, null));
    }

    public static ApiError body(int status, ErrorCode code, String path, String correlationId) {
        return ApiError.of(status, code, code.defaultMessage(), path, correlationId, null);
    }

    public static Instant now() {
        return Instant.now();
    }
}
