package com.finova.transaction.config;

import com.finova.common.web.CorrelationId;
import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Propagates the caller's identity and correlation id onto every outbound
 * account-service call, so a single transfer can be followed across services
 * and the downstream call is authorised as the same principal.
 */
@Configuration
public class FeignConfig {

    @Bean
    RequestInterceptor correlationAndAuthorizationInterceptor() {
        return template -> {
            String correlationId = MDC.get(CorrelationId.MDC_KEY);
            if (correlationId != null && !correlationId.isBlank()) {
                template.header(CorrelationId.HEADER, correlationId);
            }
            String authorization = currentHeader("Authorization");
            if (authorization != null && !authorization.isBlank()) {
                template.header("Authorization", authorization);
            }
        };
    }

    private String currentHeader(String name) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest().getHeader(name);
        }
        return null;
    }
}