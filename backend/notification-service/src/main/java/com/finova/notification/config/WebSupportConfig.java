package com.finova.notification.config;

import com.finova.common.web.CorrelationIdFilter;
import com.finova.common.web.GlobalExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the shared web infrastructure.
 * <p>
 * Both beans live in {@code com.finova.common.web}, which is outside this service's
 * component scan, so they have to be declared explicitly: without the filter there
 * is no {@code X-Correlation-Id} on any response, and without the advice a
 * {@code BusinessException} would leave as a servlet error instead of the platform
 * {@code ApiError} envelope.
 */
@Configuration
public class WebSupportConfig {

    @Bean
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}