package com.finova.gateway.config;

import com.finova.common.error.ErrorCode;
import com.finova.common.security.JwtProperties;
import com.finova.common.security.JwtTokenProvider;
import com.finova.gateway.error.GatewayErrorResponder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * WebFlux security wiring.
 * <p>
 * Every exchange is permitted at this layer on purpose: the platform
 * authentication rules live in {@code AuthenticationGlobalFilter}, where the
 * {@code ApiError} envelope and the {@code X-Correlation-Id} header are already
 * available. This chain only disables the browser oriented defaults (CSRF,
 * form login, HTTP basic) and owns CORS preflight handling.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public JwtTokenProvider jwtTokenProvider(JwtProperties properties) {
        return new JwtTokenProvider(properties);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(GatewayProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = properties.getAllowedOrigins();
        if (origins.contains("*")) {
            configuration.setAllowedOriginPatterns(origins);
        } else {
            configuration.setAllowedOrigins(origins);
        }
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "Idempotency-Key",
                "X-Correlation-Id",
                "X-Requested-With"));
        configuration.setExposedHeaders(List.of("X-Correlation-Id", "Location"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
                                                            CorsConfigurationSource corsSource,
                                                            GatewayErrorResponder errorResponder) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(spec -> spec.anyExchange().permitAll())
                .exceptionHandling(spec -> spec
                        .authenticationEntryPoint((exchange, ex) -> errorResponder.write(exchange,
                                HttpStatus.UNAUTHORIZED.value(), ErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler((exchange, ex) -> errorResponder.write(exchange,
                                HttpStatus.FORBIDDEN.value(), ErrorCode.ACCESS_DENIED)))
                .build();
    }
}
