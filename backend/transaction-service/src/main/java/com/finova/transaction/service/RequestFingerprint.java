package com.finova.transaction.service;

import com.finova.common.support.Money;
import com.finova.transaction.dto.TransferRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 fingerprint of a transfer request, used to tell a genuine retry from
 * an idempotency key reused with a different payload.
 * <p>
 * The fingerprint is built from typed fields joined with a separator rather than
 * from the raw JSON body, so JSON key order, whitespace and formatting can never
 * change the result, while any change to a business field does.
 */
@Component
public class RequestFingerprint {

    private static final char SEPARATOR = '\u001f';

    public String of(TransferRequest request) {
        String canonical = String.join(String.valueOf(SEPARATOR),
                nullSafe(request.senderAccountId()),
                nullSafe(request.receiverAccountNumber()),
                scale(request.amount()),
                nullSafe(request.currency()),
                nullSafe(request.description()));
        return sha256(canonical);
    }

    public String sha256(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", ex);
        }
    }

    private String scale(BigDecimal value) {
        return value == null ? "" : Money.scale(value).toPlainString();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value.trim();
    }
}