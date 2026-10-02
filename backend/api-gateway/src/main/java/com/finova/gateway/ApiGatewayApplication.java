package com.finova.gateway;

import com.finova.common.security.JwtProperties;
import com.finova.gateway.config.GatewayProperties;
import com.finova.gateway.config.RateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Finova API Gateway: the single entry point of the platform.
 * <p>
 * It terminates TLS-less HTTP traffic from the Vue SPA, validates the access
 * token issued by the user-service, stamps the request correlation id,
 * enforces CORS and in-memory rate limits, and routes the call to the owning
 * service through {@code lb://} Eureka load balancing. Identity is propagated
 * downstream as {@code X-User-*} headers, so no service has to re-verify the
 * JWT against the shared secret.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties({GatewayProperties.class, RateLimitProperties.class, JwtProperties.class})
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
