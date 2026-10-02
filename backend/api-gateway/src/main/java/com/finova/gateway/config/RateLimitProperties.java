package com.finova.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory token bucket settings. No Redis or other external store is used, so
 * the limit is per gateway instance: a multi-node deployment multiplies the
 * effective ceiling by the number of gateway replicas.
 */
@ConfigurationProperties(prefix = "finova.gateway.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int capacity = 120;
    private long windowSeconds = 60L;
    private int authCapacity = 10;
    private List<String> authPaths = new ArrayList<>(List.of("/api/auth/login"));
    private long cleanupIntervalSeconds = 60L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public long getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(long windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public int getAuthCapacity() {
        return authCapacity;
    }

    public void setAuthCapacity(int authCapacity) {
        this.authCapacity = authCapacity;
    }

    public List<String> getAuthPaths() {
        return authPaths;
    }

    public void setAuthPaths(List<String> authPaths) {
        this.authPaths = authPaths;
    }

    public long getCleanupIntervalSeconds() {
        return cleanupIntervalSeconds;
    }

    public void setCleanupIntervalSeconds(long cleanupIntervalSeconds) {
        this.cleanupIntervalSeconds = cleanupIntervalSeconds;
    }
}
