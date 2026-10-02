package com.finova.account.support;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Formatting rules for account numbers.
 * <p>
 * The canonical stored form has no whitespace; every rendering the UI needs is
 * derived from it so a single value stays the source of truth.
 */
public final class AccountNumbers {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^A-Z0-9]");
    private static final Pattern CANONICAL = Pattern.compile("^[A-Z]{2}\\d{20}$");
    private static final int GROUP_SIZE = 4;
    private static final int MIN_GROUPS_TO_MASK = 4;
    private static final String MASKED_GROUP = "••••";

    private AccountNumbers() {
    }

    /** Upper-cases and strips every separator so lookups are whitespace insensitive. */
    public static String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        return NON_ALPHANUMERIC.matcher(raw.toUpperCase(Locale.ROOT)).replaceAll("");
    }

    public static boolean isCanonical(String normalised) {
        return normalised != null && CANONICAL.matcher(normalised).matches();
    }

    /** {@code TN58100001234567890123} to {@code TN58 1000 0123 4567 8901 23}. */
    public static String format(String normalised) {
        if (normalised == null || normalised.isEmpty()) {
            return normalised;
        }
        StringBuilder out = new StringBuilder(normalised.length() + 6);
        for (int index = 0; index < normalised.length(); index += GROUP_SIZE) {
            if (index > 0) {
                out.append(' ');
            }
            out.append(normalised, index, Math.min(index + GROUP_SIZE, normalised.length()));
        }
        return out.toString();
    }

    /** {@code TN58 •••• •••• 8901 23}: the bank prefix and the last two groups stay visible. */
    public static String mask(String normalised) {
        if (normalised == null || normalised.isEmpty()) {
            return normalised;
        }
        String[] groups = format(normalised).split(" ");
        if (groups.length < MIN_GROUPS_TO_MASK) {
            return MASKED_GROUP;
        }
        return groups[0] + " " + MASKED_GROUP + " " + MASKED_GROUP + " "
            + groups[groups.length - 2] + " " + groups[groups.length - 1];
    }
}
