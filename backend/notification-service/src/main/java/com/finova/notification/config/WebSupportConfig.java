package com.finova.notification.config;

import com.finova.common.web.CorrelationIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the shared correlation filter. It is a {@code Filter} bean, so Spring
 * Boot wires it into the servlet chain and echoes {@code X-Correlation-Id} on
 * every response, including the 401 and 403 produced by the security chain.
 */
@Configuration
public class WebSupportConfig {

    @Bean
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }
}