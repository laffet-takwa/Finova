package com.finova.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sign-in payload. The password is compared against the stored BCrypt hash only. */
public record LoginRequest(
        @NotBlank @Email @Size(max = 190) String email,
        @NotBlank @Size(max = 64) String password) {
}
