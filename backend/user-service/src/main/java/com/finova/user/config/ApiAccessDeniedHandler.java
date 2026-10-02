package com.finova.user.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.common.error.ApiError;
import com.finova.common.error.ErrorCode;
import com.finova.common.web.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/** Renders a 403 as the platform {@link ApiError} envelope instead of an empty body. */
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ApiAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ErrorCode code = ErrorCode.ACCESS_DENIED;
        response.setStatus(code.httpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiError.of(code.httpStatus(), code,
                code.defaultMessage(), request.getRequestURI(),
                request.getHeader(CorrelationId.HEADER), null));
    }
}
