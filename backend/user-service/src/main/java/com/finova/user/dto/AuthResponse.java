package com.finova.user.dto;

/**
 * Result of register / login / refresh.
 * <p>
 * The refresh token is returned exactly once, in the body of the call that minted
 * it; it is never echoed again and the client is expected to replace it on every
 * rotation.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenType,
        AuthUserResponse user) {
}
