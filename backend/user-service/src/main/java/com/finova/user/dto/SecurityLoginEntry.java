package com.finova.user.dto;

import java.time.Instant;

/**
 * One entry in the customer's sign-in history.
 * <p>
 * {@code location} is always null and {@code locationSource} on the enclosing
 * response is {@code NOT_PROVIDED_BY_BACKEND}: this service records the address it
 * saw, and nothing else. Resolving that address to a place would mean either
 * shipping a geolocation database or calling a third party with a customer's IP,
 * and guessing is worse than saying nothing. The UI renders the IP and no more.
 */
public record SecurityLoginEntry(
        Instant occurredAt,
        String ipAddress,
        String device,
        String userAgent,
        String location,
        String result) {
}
