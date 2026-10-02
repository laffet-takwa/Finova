package com.finova.user.mapper;

import java.util.Locale;

/**
 * Value helpers referenced from MapStruct {@code @Mapping(expression = ...)}.
 * <p>
 * These live here, as public static methods on a final class, on purpose. A
 * single-argument {@code String -> String} helper declared as a {@code default}
 * method on a {@code @Mapper} interface is interpreted by MapStruct as a global type
 * conversion and is then applied to <em>every</em> string property of every mapped
 * type, which silently rewrites unrelated fields. Referencing a static helper from an
 * explicit expression keeps the normalisation where it was asked for.
 */
public final class MappingSupport {

    private MappingSupport() {
    }

    /** Email is stored and compared trimmed and lower-cased, so the same rule is applied here. */
    public static String normaliseEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        return trimmed.isEmpty() ? trimmed : trimmed.toLowerCase(Locale.ROOT);
    }

    /** Names are stored trimmed; internal whitespace is left alone. */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
