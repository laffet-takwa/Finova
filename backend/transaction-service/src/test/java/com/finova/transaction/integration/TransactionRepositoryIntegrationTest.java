package com.finova.transaction.integration;

import com.finova.common.domain.AccountStatus;
import com.finova.transaction.TransactionServiceApplication;
import com.finova.transaction.domain.LedgerAccount;
import com.finova.transaction.domain.Transaction;
import com.finova.transaction.domain.TransactionEventMarker;
import com.finova.transaction.repository.LedgerAccountRepository;
import com.finova.transaction.repository.TransactionEventMarkerRepository;
import com.finova.transaction.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.finova.transaction.support.TestFixtures.transaction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TransactionRepository - Flyway matches the mapping and the constraints bite")
@SpringBootTest(classes = TransactionServiceApplication.class)
@ActiveProfiles("test")
class TransactionRepositoryIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;
    @Autowired
    private TransactionEventMarkerRepository markerRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldStartWithFlywayAppliedAndHibernateValidated() {
        assertTrue(transactionRepository.count() >= 0);
        assertTrue(ledgerAccountRepository.count() >= 0);
    }

    @Test
    void shouldEnforceTheUniqueIdempotencyKey() {
        String key = "idem-" + UUID.randomUUID();
        Transaction first = transaction("tx-b", "TX-20261001-00021", "acct-s", "acct-r", "10.000", "PENDING");
        first.setIdempotencyKey(key);
        transactionRepository.saveAndFlush(first);

        Transaction duplicate = transaction("tx-c", "TX-20261001-00022", "acct-s", "acct-r", "10.000", "PENDING");
        duplicate.setIdempotencyKey(key);

        assertThrows(DataIntegrityViolationException.class,
                () -> transactionRepository.saveAndFlush(duplicate));
    }

    @Test
    void shouldEnforceTheUniqueReference() {
        transactionRepository.saveAndFlush(
                transaction("tx-d", "TX-20261001-00023", "acct-s", "acct-r", "10.000", "PENDING"));

        Transaction duplicate = transaction("tx-e", "TX-20261001-00023", "acct-s", "acct-r", "10.000", "PENDING");

        assertThrows(DataIntegrityViolationException.class,
                () -> transactionRepository.saveAndFlush(duplicate));
    }

    @Test
    void shouldEnforceTheUniqueAccountIdOnTheLedgerProjection() {
        LedgerAccount first = ledgerAccountRepository.saveAndFlush(ledger("acct-unique-1", "500.000"));

        assertThrows(DataIntegrityViolationException.class,
                () -> ledgerAccountRepository.saveAndFlush(ledger("acct-unique-1", "400.000")));
        assertTrue(first.getId().length() == 36);
    }

    @Test
    void shouldRejectANegativeLedgerBalance() {
        assertThrows(DataIntegrityViolationException.class,
                () -> ledgerAccountRepository.saveAndFlush(ledger("acct-negative", "-0.001")));
    }

    @Test
    void shouldAcceptAZeroLedgerBalance() {
        LedgerAccount account = ledgerAccountRepository.saveAndFlush(ledger("acct-zero", "0.000"));

        assertEquals(0, account.getBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    void shouldRejectANonPositiveTransactionAmount() {
        Transaction invalid = transaction("tx-neg", "TX-20261001-00024", "acct-s", "acct-r", "-5.000", "PENDING");

        assertThrows(DataIntegrityViolationException.class,
                () -> transactionRepository.saveAndFlush(invalid));
    }

    @Test
    void shouldRoundTripRiskReasonsAsJsonb() {
        Transaction flagged = transaction("tx-json", "TX-20261001-00025", "acct-s", "acct-r", "10.000", "FLAGGED");
        flagged.setRiskReasons(List.of("Unusual amount", "New beneficiary"));
        flagged.setRiskScore(88);
        flagged.setRiskLevel("HIGH");
        transactionRepository.saveAndFlush(flagged);
        entityManager.clear();

        Transaction reloaded = transactionRepository.findById("tx-json").orElseThrow();
        assertEquals(List.of("Unusual amount", "New beneficiary"), reloaded.getRiskReasons());
        assertEquals(88, reloaded.getRiskScore());
        assertEquals("HIGH", reloaded.getRiskLevel());
    }

    @Test
    void shouldLockTheTransactionRowForUpdate() {
        transactionRepository.saveAndFlush(
                transaction("tx-lock", "TX-20261001-00026", "acct-s", "acct-r", "10.000", "PENDING"));
        entityManager.clear();

        Transaction locked = transactionRepository.findByIdForUpdate("tx-lock").orElseThrow();
        locked.setFailureReason("under lock");
        transactionRepository.saveAndFlush(locked);
        entityManager.clear();

        assertEquals("under lock", transactionRepository.findById("tx-lock").orElseThrow().getFailureReason());
    }

    @Test
    void shouldEnforceTheUniqueTopicAndEventIdMarker() {
        markerRepository.saveAndFlush(marker("tx-mark", "transaction.approved", "evt-duplicate"));

        assertThrows(DataIntegrityViolationException.class,
                () -> markerRepository.saveAndFlush(marker("tx-mark", "transaction.approved", "evt-duplicate")));
        assertTrue(markerRepository.existsByTopicAndEventId("transaction.approved", "evt-duplicate"));
    }

    @Test
    void shouldAllowTheSameEventIdOnAnotherTopic() {
        markerRepository.saveAndFlush(marker("tx-mark", "transaction.approved", "evt-shared"));
        markerRepository.saveAndFlush(marker("tx-mark", "transaction.flagged", "evt-shared"));

        assertTrue(markerRepository.existsByTopicAndEventId("transaction.approved", "evt-shared"));
        assertTrue(markerRepository.existsByTopicAndEventId("transaction.flagged", "evt-shared"));
    }

    private TransactionEventMarker marker(String transactionId, String topic, String eventId) {
        TransactionEventMarker marker = new TransactionEventMarker();
        marker.setId(UUID.randomUUID().toString());
        marker.setTopic(topic);
        marker.setEventId(eventId);
        marker.setTransactionId(transactionId);
        marker.setProcessedAt(Instant.now());
        return marker;
    }

    private LedgerAccount ledger(String accountId, String balance) {
        LedgerAccount account = new LedgerAccount();
        account.setId(UUID.randomUUID().toString());
        account.setAccountId(accountId);
        account.setAccountNumber("TN58000000" + String.format("%09d", Math.abs(accountId.hashCode() % 1000000000)));
        account.setUserId("user-" + accountId);
        account.setAccountType("CHECKING");
        account.setCurrency("TND");
        account.setBalance(new BigDecimal(balance));
        account.setStatus(AccountStatus.ACTIVE.name());
        return account;
    }
}