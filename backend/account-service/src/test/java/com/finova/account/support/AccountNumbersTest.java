package com.finova.account.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountNumbersTest {

    @Test
    void shouldNormaliseAFormattedAccountNumber() {
        assertEquals("TN58100001234567890123", AccountNumbers.normalise("tn58 1000 0123 4567 8901 23"));
        assertEquals("TN58100001234567890123", AccountNumbers.normalise("TN58100001234567890123"));
        assertEquals("TN58100001234567890123", AccountNumbers.normalise("  TN58-1000.0123.4567.8901.23  "));
    }

    @Test
    void shouldFormatInGroupsOfFour() {
        assertEquals("TN58 1000 0123 4567 8901 23", AccountNumbers.format("TN58100001234567890123"));
    }

    @Test
    void shouldMaskTheMiddleGroupsAndKeepTheTail() {
        assertEquals("TN58 •••• •••• 8901 23", AccountNumbers.mask("TN58100001234567890123"));
    }

    @Test
    void shouldRecogniseTheCanonicalForm() {
        assertTrue(AccountNumbers.isCanonical("TN58100001234567890123"));
        assertFalse(AccountNumbers.isCanonical("TN5810000123"));
        assertFalse(AccountNumbers.isCanonical("T58100001234567890123"));
        assertFalse(AccountNumbers.isCanonical("TN5810000123456789012A"));
        assertFalse(AccountNumbers.isCanonical(null));
    }
}
