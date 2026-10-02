package com.finova.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Sign-up payload.
 * <p>
 * {@code password} is write-only: it is validated here, hashed immediately by the
 * service, and never mapped to a response, an audit payload or a log line.
 */
public record RegisterRequest(
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @NotBlank @Email @Size(max = 190) String email,
        @Size(max = 32) String phone,
        @NotBlank @Size(min = 8, max = 64) String password,
        @Size(max = 64) String confirmPassword) {

    /** Platform password policy: one letter, one digit, at least eight characters. */
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$",
            message = "must be at least 8 characters and contain a letter and a digit")
    public String password() {
        return password;
    }
}
