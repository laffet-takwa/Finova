package com.finova.account.dto;

import com.finova.common.domain.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "StatusUpdateRequest", description = "Administrator status transition")
public record StatusUpdateRequest(

    @NotNull(message = "status is required")
    @Schema(allowableValues = {"ACTIVE", "BLOCKED", "CLOSED"})
    AccountStatus status,

    @Size(max = 255, message = "reason must not exceed 255 characters")
    @Schema(example = "Suspected fraudulent activity", maxLength = 255)
    String reason
) {
}
