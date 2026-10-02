package com.finova.user.dto;

/**
 * Logout payload.
 * <p>
 * Optional on purpose: the endpoint answers 204 whether or not the token resolves,
 * so the client can clear its session unconditionally instead of handling a failure
 * it cannot act on.
 */
public record LogoutRequest(String refreshToken) {
}
