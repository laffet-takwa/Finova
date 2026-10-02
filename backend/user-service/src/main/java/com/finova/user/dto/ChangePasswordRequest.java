package com.finova.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Password change payload. Both values are write-only and never echoed. */
public record ChangePasswordRequest(
        @NotBlank @Size(max = 64) String currentPassword,
        @NotBlank @Size(min = 8, max = 64)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$",
                message = "must be at least 8 characters and contain a letter and a digit")
        String newPassword) {
}
