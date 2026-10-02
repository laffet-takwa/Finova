package com.finova.notification.mapper;

import com.finova.common.domain.Currency;

import java.util.Locale;

/**
 * Formatting helpers for the administrator feed.
 * <p>
 * These live outside the mapper interface on purpose. A {@code String -> String}
 * method declared on a MapStruct mapper is picked up as a type conversion and
 * silently applied to <em>every</em> string property of every target, which would
 * append an ellipsis to titles, references and ids across the whole service. As
 * plain statics referenced only from {@code @Mapping} expressions they do exactly
 * what they say.
 */
public final class AdminHints {

    /** Number of leading characters kept in an owner hint. */
    public static final int LENGTH = 8;

    public static final String SUFFIX = "…";

    private AdminHints() {
    }

    /**
     * First {@value #LENGTH} characters of the owner id followed by an ellipsis, for
     * example {@code 3f6d9a1c…}. Case is preserved so the hint can be compared by eye
     * against an audit entry. The ellipsis is always appended, even for an id shorter
     * than the cut, so a hint is never mistaken for a complete id.
     */
    public static String maskUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        String prefix = userId.length() > LENGTH ? userId.substring(0, LENGTH) : userId;
        return prefix + SUFFIX;
    }

    /**
     * The column is free text so a future currency does not need a migration, which
     * means an unrecognised code has to degrade to null rather than break a whole feed.
     */
    public static Currency toCurrency(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Currency.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}