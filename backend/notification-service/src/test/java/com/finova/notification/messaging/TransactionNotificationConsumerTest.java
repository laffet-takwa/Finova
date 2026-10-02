package com.finova.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.common.domain.NotificationType;
import com.finova.common.event.DomainEvent;
import com.finova.common.event.Topics;
import com.finova.common.event.TransactionEvent;
import com.finova.notification.domain.Notification;
import com.finova.notification.domain.NotificationCategory;
import com.finova.notification.domain.NotificationSeverity;
import com.finova.notification.domain.OutboxEvent;
import com.finova.notification.repository.NotificationRepository;
import com.finova.notification.repository.OutboxEventRepository;
import com.finova.notification.service.NotificationFactory;
import com.finova.notification.service.NotificationIngestionService;
import com.finova.notification.service.NotificationWriter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The consumer is wired with the real factory, the real writer and mocked
 * persistence, so the assertions are about real behaviour: how many entries a
 * terminal fact produces, and what happens when Kafka delivers the same fact twice.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionNotificationConsumerTest {

    private static final String SENDER_ACCOUNT = "TN590010012345676789";
    private static final String RECEIVER_ACCOUNT = "TN590010009876544321";

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private TransactionNotificationConsumer consumer;

    /** Stands in for the partial unique index on {@code source_event_id}. */
    private final Set<String> storedSourceEventIds = new LinkedHashSet<>();
    private final List<Notification> stored = new ArrayList<>();
    private final List<OutboxEvent> outboxRows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(notificationRepository.existsBySourceEventId(anyString()))
                .thenAnswer(invocation -> storedSourceEventIds.contains(invocation.getArgument(0)));
        when(notificationRepository.saveAndFlush(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            storedSourceEventIds.add(notification.getSourceEventId());
            stored.add(notification);
            return notification;
        });
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> {
            outboxRows.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });

        NotificationWriter writer = new NotificationWriter(notificationRepository, outboxEventRepository,
                objectMapper);
        NotificationIngestionService ingestionService =
                new NotificationIngestionService(new NotificationFactory(), writer);
        consumer = new TransactionNotificationConsumer(objectMapper, ingestionService);
    }

    @Test
    @DisplayName("A settled transfer produces exactly two entries, one per party")
    void shouldProduceTwoNotificationsWhenTransferCompleted() {
        consumer.onTransactionEvent(record(Topics.TRANSACTION_COMPLETED, "evt-completed-1", true));

        assertThat(stored).hasSize(2);
        assertThat(stored).extracting(Notification::getUserId)
                .containsExactly("user-sender", "user-receiver");
        assertThat(stored).extracting(Notification::getType)
                .containsOnly(NotificationType.TRANSFER_COMPLETED);
        assertThat(stored).extracting(Notification::getTitle)
                .containsExactly("Transfer completed", "Money received");
        assertThat(stored).extracting(Notification::getSourceEventId)
                .containsExactly("evt-completed-1:SENDER", "evt-completed-1:RECEIVER");
        assertThat(outboxRows).hasSize(2);
        assertThat(outboxRows).extracting(OutboxEvent::getTopic).containsOnly(Topics.NOTIFICATION_CREATED);
        assertThat(outboxRows).extracting(OutboxEvent::getDedupeKey)
                .allSatisfy(key -> assertThat(key).startsWith(Topics.NOTIFICATION_CREATED + ":"));
    }

    @Test
    @DisplayName("The same event id delivered twice creates no duplicate")
    void shouldIgnoreRedeliveryOfTheSameEventId() {
        consumer.onTransactionEvent(record(Topics.TRANSACTION_COMPLETED, "evt-completed-1", true));
        consumer.onTransactionEvent(record(Topics.TRANSACTION_COMPLETED, "evt-completed-1", true));
        consumer.onTransactionEvent(record(Topics.TRANSACTION_COMPLETED, "evt-completed-1", true));

        assertThat(stored).hasSize(2);
        assertThat(outboxRows).hasSize(2);
        verify(notificationRepository, times(6)).existsBySourceEventId(anyString());
        verify(notificationRepository, times(2)).saveAndFlush(any(Notification.class));
    }

    @Test
    @DisplayName("A delivery that races the existence check is swallowed by the unique index")
    void shouldIgnoreDeliveryRejectedByTheUniqueIndex() {
        when(notificationRepository.existsBySourceEventId(anyString())).thenReturn(false);
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenThrow(new DataIntegrityViolationException("uq_notification_source_event"));

        consumer.onTransactionEvent(record(Topics.TRANSACTION_FAILED, "evt-failed-1", true));

        assertThat(stored).isEmpty();
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("A failed transfer notifies the sender only")
    void shouldProduceOneNotificationWhenTransferFailed() {
        consumer.onTransactionEvent(record(Topics.TRANSACTION_FAILED, "evt-failed-1", true));

        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getUserId()).isEqualTo("user-sender");
        assertThat(stored.get(0).getType()).isEqualTo(NotificationType.TRANSFER_FAILED);
        assertThat(stored.get(0).getCategory()).isEqualTo(NotificationCategory.TRANSACTIONS);
        assertThat(stored.get(0).getSeverity()).isEqualTo(
                NotificationSeverity.DANGER);
        assertThat(stored.get(0).getTitle()).isEqualTo("Transfer failed");
    }

    @Test
    @DisplayName("A held transfer notifies the sender only")
    void shouldProduceOneNotificationWhenTransferFlagged() {
        consumer.onTransactionEvent(record(Topics.TRANSACTION_FLAGGED, "evt-flagged-1", true));

        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getUserId()).isEqualTo("user-sender");
        assertThat(stored.get(0).getCategory()).isEqualTo(NotificationCategory.SECURITY);
    }

    @Test
    @DisplayName("A settled transfer with no beneficiary still reaches the sender")
    void shouldProduceOneNotificationWhenReceiverIsMissing() {
        consumer.onTransactionEvent(record(Topics.TRANSACTION_COMPLETED, "evt-no-receiver", false));

        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getUserId()).isEqualTo("user-sender");
        assertThat(stored.get(0).getTitle()).isEqualTo("Transfer completed");
    }

    @Test
    @DisplayName("The topic decides the wording, and an approval produces nothing")
    void shouldProduceNothingWhenTopicIsNotATerminalFact() {
        consumer.onTransactionEvent(record(Topics.TRANSACTION_APPROVED, "evt-approved-1", true));
        consumer.onTransactionEvent(record(Topics.TRANSACTION_CREATED, "evt-created-1", true));

        assertThat(stored).isEmpty();
        assertThat(outboxRows).isEmpty();
        verify(notificationRepository, never()).saveAndFlush(any(Notification.class));
    }

    @Test
    @DisplayName("A poison record is logged and never propagates out of the listener")
    void shouldNotPropagateWhenTheRecordCannotBeRead() {
        DomainEvent<String> malformed = new DomainEvent<>("evt-broken", null,
                Topics.TRANSACTION_COMPLETED, Instant.now(), "corr-broken",
                "transaction-service", "this payload is not a transaction");
        ConsumerRecord<String, DomainEvent> poison =
                new ConsumerRecord<>(Topics.TRANSACTION_COMPLETED, 0, 12L, "tx-1", malformed);

        consumer.onTransactionEvent(poison);

        assertThat(stored).isEmpty();
    }

    @Test
    @DisplayName("The stored entries never carry a raw account number")
    void shouldMaskAccountNumbersInStoredEntries() {
        consumer.onTransactionEvent(record(Topics.TRANSACTION_COMPLETED, "evt-completed-1", true));

        assertThat(stored).allSatisfy(notification -> {
            assertThat(notification.getMessage()).doesNotContain(SENDER_ACCOUNT);
            assertThat(notification.getMessage()).doesNotContain(RECEIVER_ACCOUNT);
        });
        assertThat(stored.get(0).getMessage()).contains("•••• 4321");
        assertThat(stored.get(1).getMessage()).contains("•••• 6789");
    }

    private ConsumerRecord<String, DomainEvent> record(String topic, String eventId, boolean withReceiver) {
        TransactionEvent payload = new TransactionEvent(
                "tx-1", "TX-20261001-00001", "acc-1", "acc-2",
                SENDER_ACCOUNT, RECEIVER_ACCOUNT, "user-sender",
                withReceiver ? "user-receiver" : null,
                new BigDecimal("250.000"), "TND", "Rent", "TRANSFER",
                "COMPLETED", 12, "LOW", List.of(), null, "user-sender", "10.0.0.1",
                Instant.now());
        DomainEvent<TransactionEvent> envelope = new DomainEvent<>(eventId, null, topic,
                Instant.now(), "corr-" + eventId, "transaction-service", payload);
        return new ConsumerRecord<>(topic, 0, 1L, "tx-1", envelope);
    }
}