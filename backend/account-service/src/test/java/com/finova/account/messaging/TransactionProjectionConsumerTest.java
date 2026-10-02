package com.finova.account.messaging;

import com.finova.account.domain.Account;
import com.finova.account.domain.BalanceProjection;
import com.finova.account.repository.AccountRepository;
import com.finova.account.repository.BalanceProjectionRepository;
import com.finova.account.support.AccountFixtures;
import com.finova.common.domain.AccountStatus;
import com.finova.common.domain.AccountType;
import com.finova.common.domain.Currency;
import com.finova.common.event.DomainEvent;
import com.finova.common.event.EventType;
import com.finova.common.event.Topics;
import com.finova.common.event.TransactionEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class TransactionProjectionConsumerTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TransactionProjectionConsumer consumer;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private BalanceProjectionRepository balanceProjectionRepository;

    @BeforeEach
    void resetTables() {
        balanceProjectionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void shouldMoveBothBalancesExactlyOnceWhenTheSameEventIsDeliveredTwice() {
        Account sender = save("sender", "user-1", "TN58100001234567890111", new BigDecimal("1000.000"));
        Account receiver = save("receiver", "user-2", "TN58100001234567890122", new BigDecimal("100.000"));
        ConsumerRecord<String, DomainEvent> record = completed("tx-1", sender.getId(), receiver.getId(),
            "300.000");

        consumer.onTransactionEvent(record);
        consumer.onTransactionEvent(record);
        consumer.onTransactionEvent(record);

        assertEquals(new BigDecimal("700.000"), reload(sender.getId()).getBalance());
        assertEquals(new BigDecimal("400.000"), reload(receiver.getId()).getBalance());
        assertEquals(1, balanceProjectionRepository.count(), "one marker row per applied transaction");
        Optional<BalanceProjection> marker = balanceProjectionRepository.findById("tx-1");
        assertTrue(marker.isPresent());
    }

    @Test
    void shouldIgnoreAFailedTransaction() {
        Account sender = save("sender", "user-1", "TN58100001234567890111", new BigDecimal("1000.000"));
        Account receiver = save("receiver", "user-2", "TN58100001234567890122", new BigDecimal("100.000"));
        ConsumerRecord<String, DomainEvent> record = new ConsumerRecord<>(Topics.TRANSACTION_FAILED, 0, 1L, "tx-2",
            DomainEvent.of(EventType.TRANSACTION_FAILED, Topics.TRANSACTION_FAILED, "transaction-service",
                "test-correlation-id", transaction("tx-2", sender.getId(), receiver.getId(), "300.000", "FAILED")));

        consumer.onTransactionEvent(record);

        assertEquals(new BigDecimal("1000.000"), reload(sender.getId()).getBalance());
        assertEquals(new BigDecimal("100.000"), reload(receiver.getId()).getBalance());
        assertEquals(0, balanceProjectionRepository.count());
    }

    @Test
    void shouldNotKillTheConsumerOnAnUnreadableRecord() {
        ConsumerRecord<String, DomainEvent> broken =
            new ConsumerRecord<>(Topics.TRANSACTION_COMPLETED, 0, 2L, "tx-3", null);

        consumer.onTransactionEvent(broken);

        assertEquals(0, balanceProjectionRepository.count());
    }

    private Account save(String id, String userId, String accountNumber, BigDecimal balance) {
        Account account = AccountFixtures.account(id, userId, accountNumber, AccountType.CHECKING,
            Currency.TND, balance, AccountStatus.ACTIVE);
        return accountRepository.save(account);
    }

    private Account reload(String id) {
        return accountRepository.findById(id).orElseThrow();
    }

    private static ConsumerRecord<String, DomainEvent> completed(String transactionId, String senderId,
                                                                 String receiverId, String amount) {
        return new ConsumerRecord<>(Topics.TRANSACTION_COMPLETED, 0, 0L, transactionId,
            DomainEvent.of(EventType.TRANSACTION_COMPLETED, Topics.TRANSACTION_COMPLETED, "transaction-service",
                "test-correlation-id", transaction(transactionId, senderId, receiverId, amount, "COMPLETED")));
    }

    private static TransactionEvent transaction(String transactionId, String senderId, String receiverId,
                                                String amount, String status) {
        return new TransactionEvent(transactionId, "TX-20260101-00001", senderId, receiverId,
            "TN58100001234567890111", "TN58100001234567890122", "user-1", "user-2",
            new BigDecimal(amount), "TND", "Transfer", "TRANSFER", status,
            10, "LOW", List.of(), null, "user-1", "127.0.0.1", Instant.now());
    }
}
