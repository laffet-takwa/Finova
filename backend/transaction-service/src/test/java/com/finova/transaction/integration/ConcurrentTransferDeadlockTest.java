package com.finova.transaction.integration;

import com.finova.common.domain.AccountStatus;
import com.finova.common.domain.TransactionStatus;
import com.finova.transaction.TransactionServiceApplication;
import com.finova.transaction.domain.LedgerAccount;
import com.finova.transaction.domain.Transaction;
import com.finova.transaction.repository.LedgerAccountRepository;
import com.finova.transaction.repository.LedgerEventRepository;
import com.finova.transaction.repository.TransactionRepository;
import com.finova.transaction.service.ReferenceGenerator;
import com.finova.transaction.service.TransferSettlementService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Concurrent transfers in opposite directions never deadlock")
@SpringBootTest(classes = TransactionServiceApplication.class)
@ActiveProfiles("test")
class ConcurrentTransferDeadlockTest extends PostgresIntegrationTestBase {

    private static final String APPROVED = "transaction.approved";
    private static final BigDecimal OPENING = new BigDecimal("10000.000");

    @Autowired
    private TransferSettlementService settlementService;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private ReferenceGenerator referenceGenerator;
    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;
    @Autowired
    private LedgerEventRepository ledgerEventRepository;
    @Autowired
    private EntityManager entityManager;

    private String accountA;
    private String accountB;

    @BeforeEach
    void setUp() {
        accountA = ledgerAccountRepository.save(ledger("A", OPENING)).getAccountId();
        accountB = ledgerAccountRepository.save(ledger("B", OPENING)).getAccountId();
    }

    @RepeatedTest(5)
    void shouldCompleteBothDirectionsWithoutDeadlock() throws Exception {
        int pairs = 6;
        List<Transaction> transactions = new ArrayList<>();
        for (int index = 0; index < pairs; index++) {
            transactions.add(pending(accountA, accountB, "50.000"));
            transactions.add(pending(accountB, accountA, "50.000"));
        }

        AtomicInteger lockFailures = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<CompletableFuture<Void>> workers = List.of(
                    settleAll(executor, transactions, lockFailures, "odd"),
                    settleAll(executor, transactions.subList(1, transactions.size()), lockFailures, "even"));
            for (CompletableFuture<Void> worker : workers) {
                worker.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(0, lockFailures.get(), "a deadlock or lock timeout was observed");
        assertBalancesConserved();
        assertTrue(ledgerEventRepository.count() >= pairs * 2L * 2L,
                "expected both ledger legs per settled transfer");
    }

    @Test
    void shouldNotDeadlockUnderAHeavyContentionBurst() throws Exception {
        List<Transaction> transactions = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            transactions.add(pending(accountA, accountB, "10.000"));
            transactions.add(pending(accountB, accountA, "10.000"));
        }
        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int worker = 0; worker < 4; worker++) {
                int offset = worker;
                futures.add(executor.submit(() -> {
                    for (int index = offset; index < transactions.size(); index += 4) {
                        Transaction transaction = transactions.get(index);
                        settlementService.settle(transaction.getId(), APPROVED,
                                "evt-burst-" + transaction.getId());
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } catch (ExecutionException ex) {
            assertTrue(!(ex.getCause() instanceof DeadlockLoserDataAccessException),
                    "deadlock detected: " + ex.getCause().getMessage());
            throw ex;
        } finally {
            executor.shutdownNow();
        }

        assertBalancesConserved();
    }

    @Test
    void shouldSettleATransactionOnlyOnceWhenTheDecisionIsRedelivered() {
        Transaction transaction = pending(accountA, accountB, "120.000");

        settlementService.settle(transaction.getId(), APPROVED, "evt-once");
        settlementService.settle(transaction.getId(), APPROVED, "evt-once");

        entityManager.clear();
        BigDecimal finalA = ledgerAccountRepository.findByAccountId(accountA).orElseThrow().getBalance();
        assertEquals(0, finalA.compareTo(OPENING.subtract(new BigDecimal("120.000"))),
                "a redelivered decision moved the money twice");
        assertEquals(TransactionStatus.COMPLETED.name(),
                transactionRepository.findById(transaction.getId()).orElseThrow().getStatus());
    }

    private void assertBalancesConserved() {
        entityManager.clear();
        BigDecimal finalA = ledgerAccountRepository.findByAccountId(accountA).orElseThrow().getBalance();
        BigDecimal finalB = ledgerAccountRepository.findByAccountId(accountB).orElseThrow().getBalance();
        assertEquals(0, finalA.add(finalB).compareTo(OPENING.multiply(BigDecimal.valueOf(2))),
                "money was created or destroyed: A=" + finalA + " B=" + finalB);
        assertTrue(finalA.signum() >= 0 && finalB.signum() >= 0, "a balance went negative");
    }

    private CompletableFuture<Void> settleAll(ExecutorService executor, List<Transaction> transactions,
                                              AtomicInteger lockFailures, String label) {
        return CompletableFuture.runAsync(() -> {
            for (Transaction transaction : transactions) {
                try {
                    settlementService.settle(transaction.getId(), APPROVED,
                            "evt-" + label + "-" + transaction.getId());
                } catch (PessimisticLockingFailureException | DataIntegrityViolationException ex) {
                    lockFailures.incrementAndGet();
                }
            }
        }, executor);
    }

    private Transaction pending(String senderAccountId, String receiverAccountId, String amount) {
        Transaction transaction = new Transaction();
        transaction.setId(UUID.randomUUID().toString());
        transaction.setReference(referenceGenerator.next());
        transaction.setIdempotencyKey("conc-" + UUID.randomUUID());
        transaction.setRequestFingerprint("c".repeat(64));
        transaction.setSenderAccountId(senderAccountId);
        transaction.setReceiverAccountId(receiverAccountId);
        transaction.setSenderUserId("user-A");
        transaction.setReceiverUserId("user-B");
        transaction.setAmount(new BigDecimal(amount));
        transaction.setCurrency("TND");
        transaction.setFee(new BigDecimal("0.000"));
        transaction.setDescription("Concurrent transfer");
        transaction.setType("TRANSFER");
        transaction.setStatus(TransactionStatus.PENDING.name());
        transaction.setCorrelationId("conc-" + transaction.getId());
        return transactionRepository.save(transaction);
    }

    private LedgerAccount ledger(String alias, BigDecimal balance) {
        LedgerAccount account = new LedgerAccount();
        account.setId(UUID.randomUUID().toString());
        account.setAccountId("acct-" + alias + "-" + UUID.randomUUID().toString().substring(0, 8));
        account.setAccountNumber("TN5800" + Math.abs(account.getAccountId().hashCode() % 100000000));
        account.setUserId("user-" + alias);
        account.setAccountType("CHECKING");
        account.setCurrency("TND");
        account.setBalance(balance);
        account.setStatus(AccountStatus.ACTIVE.name());
        return account;
    }
}