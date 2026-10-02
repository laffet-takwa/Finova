package com.finova.common.security;

import java.security.Principal;

/**
 * Authenticated caller resolved from the JWT by each service.
 * <p>
 * Downstream services trust the gateway-validated token, so the principal is
 * always complete: there is no database lookup on the hot path.
 */
public record AuthenticatedUser(
        String userId,
        String email,
        String role,
        String correlationId
) implements Principal {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_CUSTOMER = "CUSTOMER";

    public boolean isAdmin() {
        return ROLE_ADMIN.equalsIgnoreCase(role);
    }

    public String authority() {
        return "ROLE_" + (isAdmin() ? ROLE_ADMIN : ROLE_CUSTOMER);
    }

    @Override
    public String getName() {
        return userId;
    }
}
