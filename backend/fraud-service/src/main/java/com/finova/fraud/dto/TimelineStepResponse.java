package com.finova.fraud.dto;

import java.time.Instant;

public record TimelineStepResponse(
        String key,
        String label,
        String description,
        Instant at
) {
}
