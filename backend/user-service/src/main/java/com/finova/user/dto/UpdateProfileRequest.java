package com.finova.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Profile edit payload.
 * <p>
 * {@code role} is deliberately absent: it is never something a customer can set.
 * {@code email} is accepted only so the API can answer a caller that echoes the
 * current address back, or one that tries to change it: a different address is
 * rejected with VALIDATION_ERROR rather than silently ignored, because a client
 * that believes it changed the address of record is worse than one that was told
 * it did not. Changing the address needs its own verified flow.
 */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @Size(max = 32) String phone,
        @Size(max = 190) String email) {
}