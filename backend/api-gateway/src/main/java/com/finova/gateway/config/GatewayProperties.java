package com.finova.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Gateway behaviour that the platform owner can tune without touching code.
 */
@ConfigurationProperties(prefix = "finova.gateway")
public class GatewayProperties {

    private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));
    private List<String> publicPaths = new ArrayList<>(List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/actuator/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/webjars/**",
            "/docs/**"));
    private List<String> adminPaths = new ArrayList<>(List.of("/api/fraud", "/api/users/admin"));
    private boolean adminRequiredOnAdminSegments = true;

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    public List<String> getAdminPaths() {
        return adminPaths;
    }

    public void setAdminPaths(List<String> adminPaths) {
        this.adminPaths = adminPaths;
    }

    public boolean isAdminRequiredOnAdminSegments() {
        return adminRequiredOnAdminSegments;
    }

    public void setAdminRequiredOnAdminSegments(boolean adminRequiredOnAdminSegments) {
        this.adminRequiredOnAdminSegments = adminRequiredOnAdminSegments;
    }
}
