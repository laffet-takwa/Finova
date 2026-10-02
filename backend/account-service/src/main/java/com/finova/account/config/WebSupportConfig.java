package com.finova.account.config;

import com.finova.common.web.CorrelationIdFilter;
import com.finova.common.web.GlobalExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Both types live in {@code com.finova.common.web}, which is outside this
 * service's component-scan root, so neither is auto-registered. Without the
 * explicit beans a {@code BusinessException} would escape as a raw servlet
 * 500 with no {@code ApiError} envelope, and every client error branch would
 * silently break.
 */
@Configuration
public class WebSupportConfig {

    @Bean
    CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }

    @Bean
    GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}