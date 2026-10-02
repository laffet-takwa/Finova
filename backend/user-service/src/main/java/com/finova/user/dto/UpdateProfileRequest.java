package com.finova.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Profile edit payload.
 * <p>
 * Email and role are deliberately absent: changing the address of record is a
 * separate, verified flow, and the role is never something a customer can set.
 */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @Size(max = 32) String phone) {
}
