package com.finova.account.dto;

import com.finova.account.support.AccountNumbers;

import java.util.Locale;

/**
 * Optional filters for {@code GET /api/accounts}. Values are normalised here so a
 * caller may pass a formatted account number in any case and an email with
 * surrounding whitespace, exactly as the beneficiary lookup already tolerates.
 */
public record AccountListFilter(String userId, String accountNumber, String email) {

    public static AccountListFilter of(String userId, String accountNumber, String email) {
        return new AccountListFilter(trimToNull(userId), normaliseAccountNumber(accountNumber), normaliseEmail(email));
    }

    /**
     * Forces the owner scope, which is how a CUSTOMER call can never escape its own
     * accounts: any {@code userId} supplied by the caller is replaced by the caller id.
     */
    public AccountListFilter scopedTo(String scopedUserId) {
        return new AccountListFilter(scopedUserId, accountNumber, email);
    }

    public boolean hasOwnerFilter() {
        return userId != null || email != null;
    }

    private static String normaliseAccountNumber(String raw) {
        String normalised = AccountNumbers.normalise(raw);
        return normalised == null || normalised.isEmpty() ? null : normalised;
    }

    private static String normaliseEmail(String raw) {
        String trimmed = trimToNull(raw);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }
}