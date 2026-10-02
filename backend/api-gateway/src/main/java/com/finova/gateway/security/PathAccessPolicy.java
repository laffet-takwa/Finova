package com.finova.gateway.security;

import com.finova.gateway.config.GatewayProperties;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;
import java.util.Locale;

/**
 * Decides whether a request may pass through the gateway without a token and
 * whether a valid token is still not enough (admin paths).
 */
@Component
public class PathAccessPolicy {

    private static final String ADMIN_SEGMENT = "admin";

    private final List<PathPattern> publicPatterns;
    private final List<String> adminPrefixes;
    private final boolean adminSegmentRuleEnabled;

    public PathAccessPolicy(GatewayProperties properties) {
        PathPatternParser parser = new PathPatternParser();
        this.publicPatterns = properties.getPublicPaths().stream()
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .map(parser::parse)
                .toList();
        this.adminPrefixes = properties.getAdminPaths().stream()
                .map(String::trim)
                .filter(prefix -> !prefix.isEmpty())
                .map(prefix -> prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix)
                .toList();
        this.adminSegmentRuleEnabled = properties.isAdminRequiredOnAdminSegments();
    }

    public boolean isPublic(String path) {
        PathContainer candidate = PathContainer.parsePath(path);
        for (PathPattern pattern : publicPatterns) {
            if (pattern.matches(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when the path is listed in {@code finova.gateway.admin-paths} (prefix
     * match, so {@code /api/fraud} covers {@code /api/fraud/**}) or when it
     * contains an {@code admin} path segment, in which case the caller must hold
     * the ADMIN role.
     */
    public boolean requiresAdmin(String path) {
        String normalised = normalise(path);
        for (String prefix : adminPrefixes) {
            if (normalised.equals(prefix) || normalised.startsWith(prefix + "/")) {
                return true;
            }
        }
        return adminSegmentRuleEnabled && hasAdminSegment(path);
    }

    public boolean isPublicRequest(ServerHttpRequest request) {
        return isPublic(request.getPath().pathWithinApplication().value());
    }

    public boolean requiresAdminRequest(ServerHttpRequest request) {
        return requiresAdmin(request.getPath().pathWithinApplication().value());
    }

    private String normalise(String path) {
        String value = path.isEmpty() ? "/" : path;
        if (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private boolean hasAdminSegment(String path) {
        for (String segment : path.split("/")) {
            if (ADMIN_SEGMENT.equalsIgnoreCase(segment)) {
                return true;
            }
        }
        return false;
    }
}
