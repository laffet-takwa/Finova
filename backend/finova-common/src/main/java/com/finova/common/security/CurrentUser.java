package com.finova.common.security;

import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Static access to the caller resolved from the current request's JWT. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AuthenticatedUser> get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    public static AuthenticatedUser require() {
        return get().orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
    }

    public static String userId() {
        return require().userId();
    }

    public static String role() {
        return require().role();
    }

    public static boolean isAdmin() {
        return get().map(AuthenticatedUser::isAdmin).orElse(false);
    }

    /**
     * Guards ownership: administrators bypass the check, everyone else must
     * match the resource owner exactly.
     */
    public static void checkOwnershipOrAdmin(String ownerId) {
        AuthenticatedUser user = require();
        if (!user.isAdmin() && !user.userId().equals(ownerId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "You are not allowed to access this resource.");
        }
    }
}
