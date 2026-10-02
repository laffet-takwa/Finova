package com.finova.user.dto;

import jakarta.validation.constraints.NotBlank;

/** Redeems a refresh token for a fresh pair. The token must carry {@code typ=refresh}. */
public record RefreshTokenRequest(@NotBlank String refreshToken) {
}
