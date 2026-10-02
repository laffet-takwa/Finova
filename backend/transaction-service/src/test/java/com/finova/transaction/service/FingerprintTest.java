package com.finova.transaction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.transaction.dto.TransferRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@DisplayName("RequestFingerprint - canonical SHA-256 of a transfer payload")
class FingerprintTest {

    private final RequestFingerprint fingerprint = new RequestFingerprint();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private TransferRequest request(String amount) {
        return new TransferRequest("acct-sender", "TN5800000000000002", new BigDecimal(amount), "TND",
                "Monthly payment");
    }

    @Test
    void shouldProduceTheSameFingerprintForTheSamePayload() {
        assertEquals(fingerprint.of(request("250.00")), fingerprint.of(request("250.00")));
    }

    @Test
    void shouldProduceTheSameFingerprintForEquivalentAmountScales() {
        assertEquals(fingerprint.of(request("250.00")), fingerprint.of(request("250.000")));
    }

    @Test
    void shouldProduceADifferentFingerprintWhenTheAmountChanges() {
        assertNotEquals(fingerprint.of(request("250.00")), fingerprint.of(request("250.01")));
    }

    @Test
    void shouldProduceADifferentFingerprintWhenTheReceiverChanges() {
        TransferRequest other = new TransferRequest("acct-sender", "TN5800000000000003",
                new BigDecimal("250.00"), "TND", "Monthly payment");

        assertNotEquals(fingerprint.of(request("250.00")), fingerprint.of(other));
    }

    @Test
    void shouldProduceADifferentFingerprintWhenTheDescriptionChanges() {
        TransferRequest other = new TransferRequest("acct-sender", "TN5800000000000002",
                new BigDecimal("250.00"), "TND", "Rent payment");

        assertNotEquals(fingerprint.of(request("250.00")), fingerprint.of(other));
    }

    @Test
    void shouldIgnoreJsonKeyOrder() throws Exception {
        String first = "{\"senderAccountId\":\"acct-sender\",\"receiverAccountNumber\":\"TN5800000000000002\","
                + "\"amount\":250.00,\"currency\":\"TND\",\"description\":\"Monthly payment\"}";
        String second = "{\"description\":\"Monthly payment\",\"currency\":\"TND\",\"amount\":250.00,"
                + "\"receiverAccountNumber\":\"TN5800000000000002\",\"senderAccountId\":\"acct-sender\"}";

        TransferRequest firstRequest = objectMapper.readValue(first, TransferRequest.class);
        TransferRequest secondRequest = objectMapper.readValue(second, TransferRequest.class);

        assertEquals(fingerprint.of(firstRequest), fingerprint.of(secondRequest));
    }

    @Test
    void shouldProduceSixtyFourHexCharacters() {
        String value = fingerprint.of(request("250.00"));

        assertEquals(64, value.length());
        assertEquals(value.toLowerCase(java.util.Locale.ROOT), value);
    }

    @Test
    void shouldNotCollideWhenFieldOrderIsSwappedInTheCanonicalForm() {
        TransferRequest first = new TransferRequest("acct-sender", "TN58", new BigDecimal("1.000"), "TND", null);
        TransferRequest second = new TransferRequest("acct-sender", "TN58", new BigDecimal("1.000"), "TND", " ");

        assertEquals(fingerprint.of(first), fingerprint.of(second));
    }

    @Test
    void shouldTreatNullAndBlankDescriptionAsEquivalent() {
        Set<String> fingerprints = new HashSet<>();
        fingerprints.add(fingerprint.of(new TransferRequest("a", "b", new BigDecimal("1"), "TND", null)));
        fingerprints.add(fingerprint.of(new TransferRequest("a", "b", new BigDecimal("1"), "TND", "")));
        fingerprints.add(fingerprint.of(new TransferRequest("a", "b", new BigDecimal("1"), "TND", "  ")));

        assertEquals(1, fingerprints.size());
    }
}