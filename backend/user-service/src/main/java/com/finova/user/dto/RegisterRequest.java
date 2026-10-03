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
 * <p>
 * Every constraint carries an explicit English message. The default messages are
 * resolved against the server locale, so on a machine configured for French the API
 * answered "la taille doit être comprise entre 8 et 64" to an English interface —
 * and these strings are rendered verbatim in the sign-up form.
 */
public record RegisterRequest(
        @NotBlank(message = "is required")
        @Size(max = 80, message = "must be 80 characters or fewer")
        String firstName,
        @NotBlank(message = "is required")
        @Size(max = 80, message = "must be 80 characters or fewer")
        String lastName,
        @NotBlank(message = "is required")
        @Email(message = "must be a valid email address")
        @Size(max = 190, message = "must be 190 characters or fewer")
        String email,
        @Size(max = 32, message = "must be 32 characters or fewer")
        String phone,
        @NotBlank(message = "is required")
        @Size(min = 10, max = 72,
                message = "must be between 10 and 72 characters")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "must contain an uppercase letter, a lowercase letter and a digit")
        String password,
        @Size(max = 72, message = "must be 72 characters or fewer")
        String confirmPassword) {
}