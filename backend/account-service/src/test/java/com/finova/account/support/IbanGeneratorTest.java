package com.finova.account.support;

import com.finova.common.domain.Currency;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IbanGeneratorTest {

    private final IbanGenerator ibanGenerator = new IbanGenerator();

    @Test
    void shouldGenerateTunisianIbanThatPassesMod97() {
        String iban = ibanGenerator.generate("TN58100001234567890123", Currency.TND);

        assertEquals("TN565900058100001234567890123", iban);
        assertEquals(29, iban.length());
        assertTrue(IbanGenerator.isValid(iban), iban + " must satisfy the ISO 13616 mod 97 check");
    }

    @Test
    void shouldGenerateEuroIbanThatPassesMod97() {
        String iban = ibanGenerator.generate("EU58200003456789012345", Currency.EUR);

        assertEquals("EU855900058200003456789012345", iban);
        assertTrue(IbanGenerator.isValid(iban), iban + " must satisfy the ISO 13616 mod 97 check");
    }

    @Test
    void shouldGenerateUsDollarIbanThatPassesMod97() {
        String iban = ibanGenerator.generate("US58300009876543212345", Currency.USD);

        assertEquals("US225900058300009876543212345", iban);
        assertTrue(IbanGenerator.isValid(iban), iban + " must satisfy the ISO 13616 mod 97 check");
    }

    @Test
    void shouldRejectIbanWhenASingleDigitIsAltered() {
        String iban = ibanGenerator.generate("TN58100001234567890123", Currency.TND);
        String tampered = iban.substring(0, iban.length() - 1) + (iban.endsWith("3") ? "4" : "3");

        assertFalse(IbanGenerator.isValid(tampered));
    }

    @Test
    void shouldRejectNullAndShortIban() {
        assertFalse(IbanGenerator.isValid(null));
        assertFalse(IbanGenerator.isValid("TN59"));
        assertFalse(IbanGenerator.isValid("TN59 1234 5678"));
    }

    @Test
    void shouldKeepTheBankAndBranchSegmentsOfTheNationalLayout() {
        String iban = ibanGenerator.generate("TN58100001234567890123", Currency.TND);

        assertTrue(iban.startsWith("TN"), "country code must lead");
        assertTrue(iban.contains("59" + "000"), "bank and branch segments must be present");
        assertTrue(iban.endsWith("100001234567890123"), "the account body must close the IBAN");
    }
}
