package com.finova.account.support;

import com.finova.common.domain.Currency;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.EnumMap;
import java.util.Map;

/**
 * Generates the customer facing account number.
 * <p>
 * Layout: currency prefix + 2 bank digits + 18 random digits = 22 characters,
 * which renders as {@code TN58 1000 0123 4567 8901 23}. The length is fixed by
 * that display format (five groups of four plus a two digit remainder) and by
 * the IBAN body it feeds.
 */
@Component
public class AccountNumberGenerator {

    public static final int BANK_DIGITS = 2;
    public static final int RANDOM_DIGITS = 18;

    private static final String BANK_CODE = "58";
    private static final Map<Currency, String> PREFIX_BY_CURRENCY = new EnumMap<>(Currency.class);

    static {
        PREFIX_BY_CURRENCY.put(Currency.TND, "TN");
        PREFIX_BY_CURRENCY.put(Currency.EUR, "EU");
        PREFIX_BY_CURRENCY.put(Currency.USD, "US");
    }

    private final SecureRandom random = new SecureRandom();

    public String generate(Currency currency) {
        String prefix = prefixOf(currency);
        StringBuilder number = new StringBuilder(prefix).append(BANK_CODE);
        for (int index = 0; index < RANDOM_DIGITS; index++) {
            number.append(random.nextInt(10));
        }
        return number.toString();
    }

    public static String prefixOf(Currency currency) {
        String prefix = PREFIX_BY_CURRENCY.get(currency);
        if (prefix == null) {
            throw new IllegalArgumentException("Unsupported currency: " + currency);
        }
        return prefix;
    }
}
