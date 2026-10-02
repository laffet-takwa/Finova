package com.finova.user.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.common.security.JwtAuthenticationFilter;
import com.finova.common.security.JwtProperties;
import com.finova.common.security.JwtTokenProvider;
import com.finova.common.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT security for the issuing service.
 * <p>
 * Only the four {@code /api/auth} calls that a caller cannot yet authenticate are
 * public. Everything under {@code /api/users/admin} is gated on the ADMIN authority
 * here as well as by the gateway, because the gateway does not cover {@code GET
 * /api/users} and a direct call to port 8081 must not be a way around the rule.
 * <p>
 * {@link JwtAuthenticationFilter} only accepts tokens whose {@code typ} claim is
 * {@code access}, so a refresh token presented as a bearer credential leaves the
 * request unauthenticated and the authorisation rules refuse it.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public JwtTokenProvider jwtTokenProvider(JwtProperties properties) {
        return new JwtTokenProvider(properties);
    }

    @Bean
    public ApiAccessDeniedHandler apiAccessDeniedHandler(ObjectMapper objectMapper) {
        return new ApiAccessDeniedHandler(objectMapper);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtTokenProvider tokenProvider,
                                           ObjectMapper objectMapper,
                                           ApiAccessDeniedHandler apiAccessDeniedHandler) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login",
                                "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers("/actuator/**", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html", "/docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/users/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new RestAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(apiAccessDeniedHandler))
                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
