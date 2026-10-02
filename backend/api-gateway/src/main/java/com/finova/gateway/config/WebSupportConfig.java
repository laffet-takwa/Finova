package com.finova.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.gateway.error.GatewayErrorResponder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import reactor.core.publisher.Hooks;

/**
 * Enables the scheduled rate-limit bucket cleanup and makes thread local state
 * (the logging MDC) follow the reactive pipeline across threads, so every log
 * line of one exchange carries the same correlation id.
 */
@Configuration
@EnableScheduling
public class WebSupportConfig {

    static {
        Hooks.enableAutomaticContextPropagation();
    }

    @Bean
    public GatewayErrorResponder gatewayErrorResponder(ObjectMapper objectMapper) {
        return new GatewayErrorResponder(objectMapper);
    }
}
