package com.finova.account.config;

import com.finova.common.web.CorrelationId;
import feign.Logger;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign tuning for service to service calls. The dev seeder only needs
 * reachability, so failures are surfaced as plain exceptions that the caller
 * degrades on instead of aborting the application context.
 */
@Configuration
public class FeignConfig {

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    ErrorDecoder feignErrorDecoder() {
        return (methodKey, response) -> new IllegalStateException("user-service call " + methodKey
            + " failed with HTTP " + response.status() + " on " + response.request().url());
    }

    @Bean
    RequestInterceptor correlationIdPropagatingInterceptor() {
        return template -> {
            String correlationId = MDC.get(CorrelationId.MDC_KEY);
            if (correlationId != null) {
                template.header(CorrelationId.HEADER, correlationId);
            }
            template.header("Accept", "application/json");
        };
    }
}
