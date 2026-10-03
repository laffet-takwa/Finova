package com.finova.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sign-in payload. The password is compared against the stored BCrypt hash only.
 * <p>
 * {@code password} is deliberately length-bounded at 72 without a minimum: this is the
 * sign-in of an <em>existing</em> account, and rejecting a password by length here
 * would tell an attacker which rule the credentials they already hold were created
 * under. Composition is a sign-up rule, not a sign-in one.
 */
public record LoginRequest(
        @NotBlank(message = "is required")
        @Email(message = "must be a valid email address")
        @Size(max = 190, message = "must be 190 characters or fewer")
        String email,
        @NotBlank(message = "is required")
        @Size(max = 72, message = "must be 72 characters or fewer")
        String password) {
}