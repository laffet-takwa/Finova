package com.finova.user.dto;

import com.finova.common.domain.Role;
import com.finova.common.domain.UserStatus;

import java.time.Instant;

/**
 * The caller, as returned to its own session.
 * <p>
 * Field names match the frontend {@code AuthUser} contract exactly. There is no
 * {@code passwordHash} component, and there cannot be one added later without
 * breaking that contract, which is the point.
 */
public record AuthUserResponse(
        String id,
        String firstName,
        String lastName,
        String email,
        String phone,
        Role role,
        UserStatus status,
        Instant createdAt) {
}
