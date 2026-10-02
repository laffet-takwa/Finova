package com.finova.fraud.config;

import com.finova.common.web.CorrelationId;
import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Configuration
public class FeignConfig {

    public static final String AUTHORIZATION_HEADER = "Authorization";

    /**
     * Keeps intra-platform calls traceable and authenticated: the correlation id comes from the MDC
     * so it survives onto any outbound call made from a consumer thread too, while the bearer token
     * is read straight off the inbound servlet request because the account service rejects calls it
     * cannot attribute to a user.
     */
    @Bean
    public RequestInterceptor correlationAndAuthorizationInterceptor() {
        return template -> {
            String correlationId = MDC.get(CorrelationId.MDC_KEY);
            if (correlationId != null && !correlationId.isBlank()) {
                template.header(CorrelationId.HEADER, correlationId);
            }
            String authorization = currentAuthorizationHeader();
            if (authorization != null && !authorization.isBlank()) {
                template.header(AUTHORIZATION_HEADER, authorization);
            }
        };
    }

    private String currentAuthorizationHeader() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        HttpServletRequest request = servletAttributes.getRequest();
        return request == null ? null : request.getHeader(AUTHORIZATION_HEADER);
    }
}
