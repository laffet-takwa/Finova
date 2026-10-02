package com.finova.transaction.service;

import com.finova.common.domain.AccountType;
import com.finova.common.event.EventType;
import com.finova.common.event.Topics;
import com.finova.common.event.TransactionEvent;
import com.finova.common.support.Money;
import com.finova.transaction.client.AccountLookupResponse;
import com.finova.transaction.client.AccountServiceClient;
import com.finova.transaction.domain.LedgerAccount;
import com.finova.transaction.domain.Transaction;
import com.finova.transaction.dto.TransferRequest;
import com.finova.transaction.event.OutboxService;
import com.finova.transaction.event.TransactionEventFactory;
import com.finova.transaction.mapper.AccountNumbers;
import com.finova.transaction.repository.LedgerAccountRepository;
import com.finova.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the receiver leg end to end with only the I/O boundaries mocked: the
 * real {@link LedgerProjectionService} resolves the receiver through the lookup,
 * the real {@link TransferPersistence} builds the transfer, and the captured
 * outbox payload carries the holder id the directory reported.
 */
@DisplayName("Receiver resolution - the lookup's userId reaches the published event")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReceiverUserIdPropagationTest {

    private static final String RECEIVER_NUMBER = "TN5800000000000002";
    private static final String LOOKUP_ACCOUNT_ID = "acct-receiver-0002";
    private static final String LOOKUP_USER_ID = "user-receiver-from-directory";
    private static final String SENDER_ACCOUNT_ID = "acct-sender-0001";
    private static final String SENDER_USER_ID = "user-sender";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T09:15:00Z"), ZoneOffset.UTC);

    @Mock
    private AccountServiceClient accountServiceClient;
    @Mock
    private LedgerAccountRepository ledgerAccountRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private OutboxService outbox;
    @Mock
    private ReferenceGenerator referenceGenerator;

    private LedgerProjectionService projections;
    private TransferPersistence persistence;

    @BeforeEach
    void setUp() {
        projections = new LedgerProjectionService(accountServiceClient, ledgerAccountRepository,
                new AccountNumbers());
        persistence = new TransferPersistence(transactionRepository, referenceGenerator, new TransactionEventFactory(),
                outbox, CLOCK);
        when(ledgerAccountRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(referenceGenerator.next()).thenReturn("TX-20261001-00001");
    }

    private LedgerAccount sender() {
        LedgerAccount sender = new LedgerAccount();
        sender.setId("ledger-0001");
        sender.setAccountId(SENDER_ACCOUNT_ID);
        sender.setAccountNumber("TN5800000000000001");
        sender.setUserId(SENDER_USER_ID);
        sender.setAccountType(AccountType.CHECKING.name());
        sender.setCurrency("TND");
        sender.setBalance(new BigDecimal("5000.000"));
        sender.setStatus("ACTIVE");
        return sender;
    }

    private AccountLookupResponse lookup() {
        return new AccountLookupResponse(LOOKUP_ACCOUNT_ID, LOOKUP_USER_ID, RECEIVER_NUMBER, "•••• 0002",
                AccountType.CHECKING.name(), "TND", "ACTIVE", "I. B.", "Finova");
    }

    @Test
    void shouldPublishReceiverUserIdTakenFromTheLookup() {
        when(accountServiceClient.lookupBeneficiary(RECEIVER_NUMBER)).thenReturn(lookup());
        when(ledgerAccountRepository.findByAccountId(LOOKUP_ACCOUNT_ID)).thenReturn(Optional.empty());

        LedgerAccount receiver = projections.resolveByAccountNumber(RECEIVER_NUMBER);
        TransferRequest request = new TransferRequest(SENDER_ACCOUNT_ID, RECEIVER_NUMBER,
                new BigDecimal("250.00"), "TND", "Monthly payment");
        TransferValidation validation = new TransferValidation(Money.scale(new BigDecimal("250.00")), "TND",
                Money.ZERO);

        persistence.insert(request, "idem-key-1234", "a".repeat(64), sender(), receiver, validation,
                SENDER_USER_ID, "10.0.0.1");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outbox).enqueue(eq(Topics.TRANSACTION_CREATED), anyString(),
                eq(EventType.TRANSACTION_CREATED), payload.capture());
        TransactionEvent event = (TransactionEvent) payload.getValue();
        assertEquals(LOOKUP_USER_ID, event.receiverUserId());
        assertEquals(SENDER_USER_ID, event.senderUserId());
        assertEquals(LOOKUP_ACCOUNT_ID, event.receiverAccountId());
        assertEquals(RECEIVER_NUMBER, event.receiverAccountNumber());
    }

    @Test
    void shouldPersistReceiverUserIdTakenFromTheLookup() {
        when(accountServiceClient.lookupBeneficiary(RECEIVER_NUMBER)).thenReturn(lookup());
        when(ledgerAccountRepository.findByAccountId(LOOKUP_ACCOUNT_ID)).thenReturn(Optional.empty());

        LedgerAccount receiver = projections.resolveByAccountNumber(RECEIVER_NUMBER);
        TransferRequest request = new TransferRequest(SENDER_ACCOUNT_ID, RECEIVER_NUMBER,
                new BigDecimal("40.00"), "TND", "Rent");
        TransferValidation validation = new TransferValidation(Money.scale(new BigDecimal("40.00")), "TND",
                Money.ZERO);

        ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
        persistence.insert(request, "idem-key-5678", "b".repeat(64), sender(), receiver, validation,
                SENDER_USER_ID, "10.0.0.1");
        verify(transactionRepository, times(1)).save(saved.capture());

        assertEquals(LOOKUP_USER_ID, saved.getValue().getReceiverUserId());
        assertEquals(LOOKUP_ACCOUNT_ID, saved.getValue().getReceiverAccountId());
    }

    @Test
    void shouldNotCallTheDirectoryTwiceForOneReceiverResolution() {
        when(accountServiceClient.lookupBeneficiary(RECEIVER_NUMBER)).thenReturn(lookup());
        when(ledgerAccountRepository.findByAccountId(LOOKUP_ACCOUNT_ID)).thenReturn(Optional.empty());

        projections.resolveByAccountNumber(RECEIVER_NUMBER);

        verify(accountServiceClient, times(1)).lookupBeneficiary(RECEIVER_NUMBER);
        verify(accountServiceClient, times(0)).getAccount(anyString());
    }
}