package com.finova.account.support;

import com.finova.common.domain.Currency;
import org.springframework.stereotype.Component;

/**
 * ISO 13616 / ISO 7064 MOD-97-10 IBAN generator.
 * <p>
 * Layout: country prefix + 2 check digits + {@code 59} (bank) + {@code 000}
 * (branch) + the numeric body of the account number, so a Tunisian IBAN keeps
 * its familiar {@code TNxx 59} shape. The two check digits are always the real
 * MOD-97-10 value for the final string, which {@link #isValid(String)} verifies
 * the same way every bank does: move the first four characters to the end,
 * replace letters by their numeric value and require a remainder of 1.
 */
@Component
public class IbanGenerator {

    private static final int MODULUS = 97;
    private static final String BANK_SEGMENT = "59";
    private static final String BRANCH_SEGMENT = "000";
    private static final int PREFIX_LENGTH = 2;

    public String generate(String normalisedAccountNumber, Currency currency) {
        String countryCode = AccountNumberGenerator.prefixOf(currency);
        String numericBody = normalisedAccountNumber.substring(PREFIX_LENGTH);
        String bban = BANK_SEGMENT + BRANCH_SEGMENT + numericBody;
        int checkDigits = computeCheckDigits(countryCode, bban);
        return countryCode + pad(checkDigits) + bban;
    }

    /** ISO 13616 validation: the rearranged IBAN must be congruent to 1 modulo 97. */
    public static boolean isValid(String iban) {
        if (iban == null || iban.length() < 5) {
            return false;
        }
        String normalised = AccountNumbers.normalise(iban);
        String rearranged = normalised.substring(4) + normalised.substring(0, 4);
        return modulo97(expand(rearranged)) == 1;
    }

    static int computeCheckDigits(String countryCode, String bban) {
        return 98 - modulo97(expand(bban + countryCode + "00"));
    }

    private static String pad(int checkDigits) {
        return checkDigits < 10 ? "0" + checkDigits : Integer.toString(checkDigits);
    }

    private static String expand(String alphanumeric) {
        StringBuilder digits = new StringBuilder(alphanumeric.length() * 2);
        for (int index = 0; index < alphanumeric.length(); index++) {
            char character = alphanumeric.charAt(index);
            if (character >= 'A' && character <= 'Z') {
                digits.append(character - 'A' + 10);
            } else if (character >= '0' && character <= '9') {
                digits.append(character);
            } else {
                throw new IllegalArgumentException("Illegal IBAN character: " + character);
            }
        }
        return digits.toString();
    }

    private static int modulo97(String digits) {
        int remainder = 0;
        for (int index = 0; index < digits.length(); index++) {
            remainder = (remainder * 10 + (digits.charAt(index) - '0')) % MODULUS;
        }
        return remainder;
    }
}
