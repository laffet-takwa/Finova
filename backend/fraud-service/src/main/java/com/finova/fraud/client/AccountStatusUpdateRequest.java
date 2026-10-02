package com.finova.fraud.client;

/** Body of {@code PUT /api/accounts/{id}/status} on the account service. */
public record AccountStatusUpdateRequest(
        String status,
        String reason
) {
    public static AccountStatusUpdateRequest blocked(String reason) {
        return new AccountStatusUpdateRequest("BLOCKED", reason);
    }
}
