package com.finova.transaction.support;

import com.finova.transaction.domain.LedgerAccount;
import com.finova.transaction.domain.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Fixtures shared by the transaction service tests. */
public final class TestFixtures {

    public static final String TND = "TND";
    public static final String ACTIVE = "ACTIVE";
    public static final String BLOCKED = "BLOCKED";

    private TestFixtures() {
    }

    public static LedgerAccount ledgerAccount(String accountId, String accountNumber, String userId,
                                              String status, String balance) {
        LedgerAccount account = new LedgerAccount();
        account.setId(UUID.randomUUID().toString());
        account.setAccountId(accountId);
        account.setAccountNumber(accountNumber);
        account.setUserId(userId);
        account.setAccountType("CHECKING");
        account.setCurrency(TND);
        account.setBalance(new BigDecimal(balance));
        account.setStatus(status);
        return account;
    }

    public static Transaction transaction(String id, String reference, String senderAccountId,
                                          String receiverAccountId, String amount, String status) {
        Transaction transaction = new Transaction();
        transaction.setId(id == null ? UUID.randomUUID().toString() : id);
        transaction.setReference(reference == null ? "TX-20261001-00001" : reference);
        transaction.setIdempotencyKey("idem-" + transaction.getId());
        transaction.setRequestFingerprint("f".repeat(64));
        transaction.setSenderAccountId(senderAccountId);
        transaction.setReceiverAccountId(receiverAccountId);
        transaction.setSenderAccountNumber("TN5800000000000001");
        transaction.setReceiverAccountNumber("TN5800000000000002");
        transaction.setSenderUserId("user-sender");
        transaction.setReceiverUserId("user-receiver");
        transaction.setAmount(new BigDecimal(amount));
        transaction.setCurrency(TND);
        transaction.setFee(new BigDecimal("0.000"));
        transaction.setDescription("Monthly payment");
        transaction.setType("TRANSFER");
        transaction.setStatus(status);
        transaction.setCreatedAt(Instant.parse("2026-10-01T09:15:00Z"));
        transaction.setCorrelationId("corr-1");
        transaction.setRequestedByUserId("user-sender");
        return transaction;
    }
}