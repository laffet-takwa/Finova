package com.finova.account.support;

import com.finova.common.security.AuthenticatedUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/** Populates the SecurityContext with the {@link AuthenticatedUser} principal the services expect. */
public final class SecurityContextTestSupport {

    private SecurityContextTestSupport() {
    }

    public static void asCustomer(String userId) {
        authenticate(userId, AuthenticatedUser.ROLE_CUSTOMER);
    }

    public static void asAdmin(String userId) {
        authenticate(userId, AuthenticatedUser.ROLE_ADMIN);
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(String userId, String role) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, userId + "@finova.dev", role, "test-correlation-id");
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority(principal.authority())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
