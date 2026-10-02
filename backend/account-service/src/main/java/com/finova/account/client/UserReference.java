package com.finova.account.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Minimal projection of a user-service user. Unknown fields are ignored so a
 * richer response from the user-service cannot break the seeder.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserReference(String id, String email, String firstName, String lastName, String status) {
}
