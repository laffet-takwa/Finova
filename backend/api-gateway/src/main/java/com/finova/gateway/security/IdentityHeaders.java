package com.finova.gateway.security;

import com.finova.common.security.AuthenticatedUser;
import com.finova.gateway.web.RequestHeaders;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

/**
 * The identity contract between the gateway and the downstream services.
 * <p>
 * Every inbound {@code X-User-*} header is removed before the gateway writes its
 * own, so a client can never spoof an identity by sending the header itself.
 */
@Component
public class IdentityHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_EMAIL = "X-User-Email";
    public static final String USER_ROLE = "X-User-Role";
    public static final String PREFIX = "X-User-";

    /** The request to proxy when the caller is anonymous: no identity header at all. */
    public ServerHttpRequest withoutIdentity(ServerHttpRequest request) {
        return RequestHeaders.with(request, IdentityHeaders::stripIdentity);
    }

    /** The request to proxy for an authenticated caller, with the verified identity. */
    public ServerHttpRequest withIdentity(ServerHttpRequest request, AuthenticatedUser user) {
        return RequestHeaders.with(request, headers -> {
            stripIdentity(headers);
            if (user == null) {
                return;
            }
            headers.set(USER_ID, user.userId());
            if (user.email() != null && !user.email().isBlank()) {
                headers.set(USER_EMAIL, user.email());
            }
            if (user.role() != null && !user.role().isBlank()) {
                headers.set(USER_ROLE, user.role());
            }
        });
    }

    private static void stripIdentity(HttpHeaders headers) {
        headers.keySet().removeIf(IdentityHeaders::isIdentityHeader);
    }

    private static boolean isIdentityHeader(String name) {
        return name != null && name.length() >= PREFIX.length()
                && name.regionMatches(true, 0, PREFIX, 0, PREFIX.length());
    }
}
