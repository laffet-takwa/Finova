package com.finova.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Password change payload. Both values are write-only and never echoed.
 * <p>
 * The new password is held to exactly the sign-up policy, so a customer cannot end up
 * with a weaker credential by changing it than by choosing it. The messages are
 * explicit for the same reason as in {@link RegisterRequest}: they are rendered
 * verbatim in the security screen, in whatever locale the server happens to run in.
 */
public record ChangePasswordRequest(
        @NotBlank(message = "is required")
        @Size(max = 72, message = "must be 72 characters or fewer")
        String currentPassword,
        @NotBlank(message = "is required")
        @Size(min = 10, max = 72, message = "must be between 10 and 72 characters")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "must contain an uppercase letter, a lowercase letter and a digit")
        String newPassword) {
}