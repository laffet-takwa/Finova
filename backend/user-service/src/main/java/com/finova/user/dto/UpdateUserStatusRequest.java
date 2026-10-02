package com.finova.user.dto;

import com.finova.common.domain.UserStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Administrative status change. An ADMIN may block or unblock an identity. */
public record UpdateUserStatusRequest(
        @NotNull UserStatus status,
        @Size(max = 200) String reason) {
}
