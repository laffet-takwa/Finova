package com.finova.transaction.mapper;

import org.springframework.stereotype.Component;

/**
 * Account-number formatting rules.
 * <p>
 * The ledger stores the normalised number (upper case, no separators) and every
 * customer response shows only a suffix, so a screenshot of the API can never
 * leak a usable account number.
 */
@Component
public class AccountNumbers {

    private static final String MASK = "•";

    public String normalise(String accountNumber) {
        if (accountNumber == null) {
            return null;
        }
        String stripped = accountNumber.replaceAll("[\\s\\-.]", "");
        return stripped.toUpperCase(java.util.Locale.ROOT);
    }

    public String mask(String accountNumber) {
        String normalised = normalise(accountNumber);
        if (normalised == null || normalised.isEmpty()) {
            return null;
        }
        return MASK.repeat(4) + " " + tail(normalised, 4);
    }

    public String display(String accountNumber) {
        String normalised = normalise(accountNumber);
        if (normalised == null || normalised.isEmpty()) {
            return "Account";
        }
        return "Account " + MASK.repeat(4) + " " + tail(normalised, 2);
    }

    private String tail(String normalised, int length) {
        if (normalised.length() <= length) {
            return normalised;
        }
        return normalised.substring(normalised.length() - length);
    }
}