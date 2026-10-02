package com.finova.user.dto;

import com.finova.common.domain.Role;
import com.finova.common.domain.UserStatus;

import java.time.Instant;

/**
 * Administrative view of an identity: the caller-facing fields plus the sign-in
 * timestamp.
 * <p>
 * This is also the payload {@code GET /api/users/admin/by-email} returns, which is
 * why it carries the email: the dev seeders in the account and transaction services
 * resolve demo user ids through that endpoint, and it stays ADMIN-only so it never
 * becomes a customer-facing email-enumeration oracle.
 */
public record UserSummaryResponse(
        String id,
        String firstName,
        String lastName,
        String email,
        String phone,
        Role role,
        UserStatus status,
        Instant createdAt,
        Instant lastLoginAt) {
}
