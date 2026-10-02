package com.finova.notification.service;

import com.finova.common.domain.NotificationType;
import com.finova.common.event.AccountBlockedEvent;
import com.finova.common.event.DomainEvent;
import com.finova.common.event.Topics;
import com.finova.common.event.TransactionEvent;
import com.finova.notification.domain.NotificationCategory;
import com.finova.notification.domain.NotificationSeverity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The copy and the recipient rules are the product surface here, so both are pinned
 * to the exact expected strings rather than to "something was produced".
 */
class NotificationFactoryTest {

    private static final String SENDER_ACCOUNT = "TN590010012345676789";
    private static final String RECEIVER_ACCOUNT = "TN590010009876544321";
    private static final String BLOCKED_ACCOUNT = "TN590010009876548901";
    private static final BigDecimal AMOUNT = new BigDecimal("250.000");

    private final NotificationFactory factory = new NotificationFactory();

    @Test
    @DisplayName("A settled transfer tells the sender it completed")
    void shouldBuildSenderEntryWhenTransferCompleted() {
        List<NotificationDraft> drafts = factory.fromTransaction(
                Topics.TRANSACTION_COMPLETED, completed("evt-completed-1"));

        assertThat(drafts).hasSize(2);

        NotificationDraft sender = drafts.get(0);
        assertThat(sender.recipientUserId()).isEqualTo("user-sender");
        assertThat(sender.type()).isEqualTo(NotificationType.TRANSFER_COMPLETED);
        assertThat(sender.category()).isEqualTo(NotificationCategory.TRANSACTIONS);
        assertThat(sender.severity()).isEqualTo(NotificationSeverity.SUCCESS);
        assertThat(sender.title()).isEqualTo("Transfer completed");
        assertThat(sender.message()).isEqualTo(
                "Your transfer of 250.000 TND to account •••• 4321 was successful.");
        assertThat(sender.reference()).isEqualTo("TX-20261001-00001");
        assertThat(sender.amount()).isEqualByComparingTo("250.000");
        assertThat(sender.currency()).isEqualTo("TND");
    }

    @Test
    @DisplayName("A settled transfer tells the receiver the money arrived")
    void shouldBuildReceiverEntryWhenTransferCompleted() {
        List<NotificationDraft> drafts = factory.fromTransaction(
                Topics.TRANSACTION_COMPLETED, completed("evt-completed-2"));

        NotificationDraft receiver = drafts.get(1);
        assertThat(receiver.recipientUserId()).isEqualTo("user-receiver");
        assertThat(receiver.type()).isEqualTo(NotificationType.TRANSFER_COMPLETED);
        assertThat(receiver.category()).isEqualTo(NotificationCategory.TRANSACTIONS);
        assertThat(receiver.severity()).isEqualTo(NotificationSeverity.SUCCESS);
        assertThat(receiver.title()).isEqualTo("Money received");
        assertThat(receiver.message()).isEqualTo(
                "You received 250.000 TND from account •••• 6789. Reference TX-20261001-00001.");
    }

    @Test
    @DisplayName("A failed transfer only warns the sender")
    void shouldNotifyOnlySenderWhenTransferFailed() {
        List<NotificationDraft> drafts = factory.fromTransaction(
                Topics.TRANSACTION_FAILED, failed("evt-failed-1"));

        assertThat(drafts).hasSize(1);
        NotificationDraft sender = drafts.get(0);
        assertThat(sender.recipientUserId()).isEqualTo("user-sender");
        assertThat(sender.type()).isEqualTo(NotificationType.TRANSFER_FAILED);
        assertThat(sender.category()).isEqualTo(NotificationCategory.TRANSACTIONS);
        assertThat(sender.severity()).isEqualTo(NotificationSeverity.DANGER);
        assertThat(sender.title()).isEqualTo("Transfer failed");
        assertThat(sender.message()).isEqualTo(
                "Your transfer of 250.000 TND could not be completed. Reference TX-20261001-00003.");
        assertThat(drafts).noneMatch(draft -> "user-receiver".equals(draft.recipientUserId()));
    }

    @Test
    @DisplayName("A held transfer is a security matter and only concerns the sender")
    void shouldNotifyOnlySenderWhenTransferFlagged() {
        List<NotificationDraft> drafts = factory.fromTransaction(
                Topics.TRANSACTION_FLAGGED, flagged("evt-flagged-1"));

        assertThat(drafts).hasSize(1);
        NotificationDraft sender = drafts.get(0);
        assertThat(sender.recipientUserId()).isEqualTo("user-sender");
        assertThat(sender.type()).isEqualTo(NotificationType.TRANSFER_FLAGGED);
        assertThat(sender.category()).isEqualTo(NotificationCategory.SECURITY);
        assertThat(sender.severity()).isEqualTo(NotificationSeverity.WARNING);
        assertThat(sender.title()).isEqualTo("Transfer held for review");
        assertThat(sender.message()).isEqualTo(
                "Your transfer of 15,000.000 TND is being reviewed by our security team. "
                        + "Reference TX-20261001-00002.");
        assertThat(drafts).noneMatch(draft -> "user-receiver".equals(draft.recipientUserId()));
    }

    @Test
    @DisplayName("A blocked account is a security alert for its holder")
    void shouldBuildSecurityEntryWhenAccountBlocked() {
        List<NotificationDraft> drafts = factory.fromAccountBlocked(accountBlocked("evt-blocked-1"));

        assertThat(drafts).hasSize(1);
        NotificationDraft draft = drafts.get(0);
        assertThat(draft.recipientUserId()).isEqualTo("user-owner");
        assertThat(draft.type()).isEqualTo(NotificationType.ACCOUNT_BLOCKED);
        assertThat(draft.category()).isEqualTo(NotificationCategory.SECURITY);
        assertThat(draft.severity()).isEqualTo(NotificationSeverity.DANGER);
        assertThat(draft.title()).isEqualTo("Account blocked");
        assertThat(draft.message()).isEqualTo(
                "Account •••• 8901 has been blocked. Contact support for more information.");
        assertThat(draft.amount()).isEqualByComparingTo("8210.450");
        assertThat(draft.currency()).isEqualTo("TND");
    }

    @Test
    @DisplayName("An approval is not a movement and must never reach an inbox")
    void shouldProduceNothingWhenTransactionApproved() {
        DomainEvent<TransactionEvent> approved = new DomainEvent<>("evt-approved-1", null,
                Topics.TRANSACTION_APPROVED, Instant.now(), "corr-1", "fraud-service",
                transaction(AMOUNT, "PENDING"));

        assertThat(factory.fromTransaction(Topics.TRANSACTION_APPROVED, approved)).isEmpty();
        assertThat(factory.fromTransaction(Topics.TRANSACTION_CREATED, approved)).isEmpty();
        assertThat(factory.fromTransaction("something.unknown", approved)).isEmpty();
    }

    @Test
    @DisplayName("No message ever contains a raw account number")
    void shouldMaskAccountNumbersInEveryMessage() {
        List<NotificationDraft> all = List.of(
                factory.fromTransaction(Topics.TRANSACTION_COMPLETED, completed("evt-mask-1")).get(0),
                factory.fromTransaction(Topics.TRANSACTION_COMPLETED, completed("evt-mask-1")).get(1),
                factory.fromTransaction(Topics.TRANSACTION_FAILED, failed("evt-mask-2")).get(0),
                factory.fromTransaction(Topics.TRANSACTION_FLAGGED, flagged("evt-mask-3")).get(0),
                factory.fromAccountBlocked(accountBlocked("evt-mask-4")).get(0));

        assertThat(all).allSatisfy(draft -> {
            assertThat(draft.message()).doesNotContain(SENDER_ACCOUNT);
            assertThat(draft.message()).doesNotContain(RECEIVER_ACCOUNT);
            assertThat(draft.message()).doesNotContain(BLOCKED_ACCOUNT);
            assertThat(draft.message()).doesNotContain("TN59");
            assertThat(draft.message()).doesNotContain("5900100");
        });
        assertThat(all.get(0).message()).contains("•••• 4321");
        assertThat(all.get(1).message()).contains("•••• 6789");
    }

    @Test
    @DisplayName("Exactly one entry per source event and recipient role")
    void shouldKeyEveryDraftOnTheSourceEventId() {
        List<NotificationDraft> drafts = factory.fromTransaction(
                Topics.TRANSACTION_COMPLETED, completed("evt-dedupe-1"));

        assertThat(drafts).hasSize(2);
        assertThat(drafts.stream().map(NotificationDraft::sourceEventId).distinct())
                .containsExactlyInAnyOrder("evt-dedupe-1:SENDER", "evt-dedupe-1:RECEIVER");
        assertThat(drafts.stream().map(NotificationDraft::recipientUserId).distinct()).hasSize(2);

        assertThat(factory.fromTransaction(Topics.TRANSACTION_FAILED, failed("evt-dedupe-1")))
                .extracting(NotificationDraft::sourceEventId)
                .containsExactly("evt-dedupe-1:SENDER");
        assertThat(factory.fromAccountBlocked(accountBlocked("evt-dedupe-1")))
                .extracting(NotificationDraft::sourceEventId)
                .containsExactly("evt-dedupe-1:OWNER");
    }

    @Test
    @DisplayName("A settled transfer without a beneficiary still notifies the sender")
    void shouldNotifyOnlySenderWhenReceiverIsMissing() {
        DomainEvent<TransactionEvent> event = new DomainEvent<>("evt-no-receiver", null,
                Topics.TRANSACTION_COMPLETED, Instant.now(), "corr-1", "transaction-service",
                new TransactionEvent("tx-1", "TX-20261001-00001",
                        "acc-1", null, SENDER_ACCOUNT, RECEIVER_ACCOUNT,
                        "user-sender", null, AMOUNT, "TND", "Transfer", "TRANSFER",
                        "COMPLETED", 12, "LOW", List.of(), null, "user-sender", "10.0.0.1",
                        Instant.now()));

        List<NotificationDraft> drafts = factory.fromTransaction(Topics.TRANSACTION_COMPLETED, event);

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).recipientUserId()).isEqualTo("user-sender");
    }

    @Test
    @DisplayName("A transfer to yourself never produces two entries for one person")
    void shouldNotDuplicateWhenSenderIsAlsoReceiver() {
        DomainEvent<TransactionEvent> event = new DomainEvent<>("evt-self", null,
                Topics.TRANSACTION_COMPLETED, Instant.now(), "corr-1", "transaction-service",
                new TransactionEvent("tx-1", "TX-20261001-00001",
                        "acc-1", "acc-2", SENDER_ACCOUNT, RECEIVER_ACCOUNT,
                        "user-sender", "user-sender", AMOUNT, "TND", "Transfer", "TRANSFER",
                        "COMPLETED", 12, "LOW", List.of(), null, "user-sender", "10.0.0.1",
                        Instant.now()));

        assertThat(factory.fromTransaction(Topics.TRANSACTION_COMPLETED, event)).hasSize(1);
    }

    @Test
    @DisplayName("A record with no payload is ignored instead of throwing")
    void shouldProduceNothingWhenEnvelopeIsEmpty() {
        assertThat(factory.fromTransaction(Topics.TRANSACTION_COMPLETED, null)).isEmpty();
        assertThat(factory.fromTransaction(null, null)).isEmpty();
        assertThat(factory.fromAccountBlocked(null)).isEmpty();

        DomainEvent<TransactionEvent> noSender = new DomainEvent<>("evt-x", null,
                Topics.TRANSACTION_COMPLETED, Instant.now(), "corr-1", "transaction-service",
                new TransactionEvent("tx-1", null, "acc-1", "acc-2", SENDER_ACCOUNT,
                        RECEIVER_ACCOUNT, null, "user-receiver", AMOUNT, "TND", null,
                        "TRANSFER", "COMPLETED", null, null, List.of(), null, null, null, null));
        assertThat(factory.fromTransaction(Topics.TRANSACTION_COMPLETED, noSender)).isEmpty();
    }

    private DomainEvent<TransactionEvent> completed(String eventId) {
        return new DomainEvent<>(eventId, null, Topics.TRANSACTION_COMPLETED, Instant.now(),
                "corr-completed", "transaction-service",
                transaction(AMOUNT, "COMPLETED"));
    }

    private DomainEvent<TransactionEvent> failed(String eventId) {
        return new DomainEvent<>(eventId, null, Topics.TRANSACTION_FAILED, Instant.now(),
                "corr-failed", "transaction-service",
                new TransactionEvent("tx-1", "TX-20261001-00003",
                        "acc-1", "acc-2", SENDER_ACCOUNT, RECEIVER_ACCOUNT,
                        "user-sender", "user-receiver", AMOUNT, "TND", "Rent",
                        "TRANSFER", "FAILED", 20, "MEDIUM", List.of("velocity"),
                        "insufficient balance", "user-sender", "10.0.0.1", null));
    }

    private DomainEvent<TransactionEvent> flagged(String eventId) {
        return new DomainEvent<>(eventId, null, Topics.TRANSACTION_FLAGGED, Instant.now(),
                "corr-flagged", "fraud-service",
                new TransactionEvent("tx-1", "TX-20261001-00002",
                        "acc-1", "acc-2", SENDER_ACCOUNT, RECEIVER_ACCOUNT,
                        "user-sender", "user-receiver", new BigDecimal("15000.000"), "TND",
                        "Equipment", "TRANSFER", "FLAGGED", 88, "HIGH",
                        List.of("new beneficiary", "high amount"), null, "user-sender",
                        "10.0.0.1", null));
    }

    private TransactionEvent transaction(BigDecimal amount, String status) {
        return new TransactionEvent("tx-1", "TX-20261001-00001",
                "acc-1", "acc-2", SENDER_ACCOUNT, RECEIVER_ACCOUNT,
                "user-sender", "user-receiver", amount, "TND", "Rent",
                "TRANSFER", status, 12, "LOW", List.of(), null, "user-sender",
                "10.0.0.1", Instant.now());
    }

    private DomainEvent<AccountBlockedEvent> accountBlocked(String eventId) {
        return new DomainEvent<>(eventId, null, Topics.ACCOUNT_BLOCKED, Instant.now(),
                "corr-blocked", "account-service",
                new AccountBlockedEvent("acc-1", "user-owner", BLOCKED_ACCOUNT,
                        "ACTIVE", "BLOCKED", "fraud review", "admin@finova.dev",
                        new BigDecimal("8210.450"), "TND", Instant.now()));
    }
}