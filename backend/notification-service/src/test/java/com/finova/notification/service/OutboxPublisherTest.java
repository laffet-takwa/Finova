package com.finova.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.common.event.DomainEvent;
import com.finova.common.event.EventType;
import com.finova.common.event.NotificationCreatedEvent;
import com.finova.common.event.Topics;
import com.finova.notification.domain.OutboxEvent;
import com.finova.notification.event.NotificationEventPublisher;
import com.finova.notification.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The outbox contract: a pending row is published and stamped, a row that was
 * already published is left alone, and a broker failure leaves the row pending
 * with its attempt counter incremented.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private NotificationEventPublisher eventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private OutboxDispatch dispatch;
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        dispatch = new OutboxDispatch(outboxEventRepository, eventPublisher, objectMapper, 500L);
        publisher = new OutboxPublisher(outboxEventRepository, dispatch);
    }

    @Test
    @DisplayName("A pending row is published and stamped")
    void shouldPublishAndStampAPendingRow() throws Exception {
        OutboxEvent row = pendingRow();
        when(outboxEventRepository.findById(row.getId())).thenReturn(Optional.of(row));
        when(eventPublisher.publish(eq(Topics.NOTIFICATION_CREATED), eq("demo-takwa"), any()))
                .thenReturn(CompletableFuture.completedFuture(sendResult(row.getId())));

        assertThat(dispatch.deliver(row.getId())).isTrue();

        verify(eventPublisher).publish(eq(Topics.NOTIFICATION_CREATED), eq("demo-takwa"), any());
        assertThat(row.getPublishedAt()).isNotNull();
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getLastError()).isNull();
        verify(outboxEventRepository).save(row);
    }

    @Test
    @DisplayName("A row that was already published is not republished")
    void shouldNotRepublishAnAlreadyPublishedRow() throws Exception {
        OutboxEvent row = pendingRow();
        row.setPublishedAt(Instant.now().minusSeconds(60));
        when(outboxEventRepository.findById(row.getId())).thenReturn(Optional.of(row));

        assertThat(dispatch.deliver(row.getId())).isFalse();

        verify(eventPublisher, never()).publish(any(), any(), any());
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("The stored envelope is republished with its original notification id")
    @SuppressWarnings("unchecked")
    void shouldRepublishTheStoredEnvelope() throws Exception {
        OutboxEvent row = pendingRow();
        when(outboxEventRepository.findById(row.getId())).thenReturn(Optional.of(row));
        when(eventPublisher.publish(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(sendResult(row.getId())));

        dispatch.deliver(row.getId());

        ArgumentCaptor<DomainEvent<?>> envelope = ArgumentCaptor.forClass(
                (Class<DomainEvent<?>>) (Class<?>) DomainEvent.class);
        verify(eventPublisher).publish(any(), any(), envelope.capture());
        assertThat(envelope.getValue().eventId()).isNotBlank();
        assertThat(envelope.getValue().topic()).isEqualTo(Topics.NOTIFICATION_CREATED);
        NotificationCreatedEvent payload =
                (NotificationCreatedEvent) envelope.getValue().payload();
        assertThat(payload.notificationId())
                .isEqualTo(row.getDedupeKey().substring(Topics.NOTIFICATION_CREATED.length() + 1));
        assertThat(payload.userId()).isEqualTo("demo-takwa");
    }

    @Test
    @DisplayName("A broker failure leaves the row pending and records the attempt")
    void shouldLeaveTheRowPendingWhenTheBrokerFails() {
        OutboxEvent row = pendingRow();
        when(outboxEventRepository.findById(row.getId())).thenReturn(Optional.of(row));
        CompletableFuture<SendResult<String, DomainEvent<?>>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new ExecutionException(new IllegalStateException("broker down")));
        when(eventPublisher.publish(any(), any(), any())).thenReturn(failed);

        assertThat(dispatch.deliver(row.getId())).isFalse();

        assertThat(row.getPublishedAt()).isNull();
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getLastError()).contains("broker down");
        verify(outboxEventRepository).save(row);
    }

    @Test
    @DisplayName("An unreadable payload is retried rather than dropped")
    void shouldKeepTheRowWhenThePayloadCannotBeRead() {
        OutboxEvent row = pendingRow();
        row.setPayload("not json");
        when(outboxEventRepository.findById(row.getId())).thenReturn(Optional.of(row));

        assertThat(dispatch.deliver(row.getId())).isFalse();

        assertThat(row.getPublishedAt()).isNull();
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getLastError()).contains("JsonParseException");
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("A missing row is skipped")
    void shouldSkipAMissingRow() {
        when(outboxEventRepository.findById("gone")).thenReturn(Optional.empty());

        assertThat(dispatch.deliver("gone")).isFalse();

        verify(eventPublisher, never()).publish(any(), any(), any());
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("The drain counts only the rows it actually published")
    void shouldCountOnlyTheRowsItPublished() {
        OutboxEvent pending = pendingRow();
        OutboxEvent alreadyPublished = pendingRow();
        alreadyPublished.setPublishedAt(Instant.now());
        when(outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pending, alreadyPublished)));
        when(outboxEventRepository.findById(any())).thenReturn(Optional.of(pending));
        when(eventPublisher.publish(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(sendResult(pending.getId())));

        assertThat(publisher.drain()).isEqualTo(1);
        verify(eventPublisher, times(1)).publish(any(), any(), any());
    }

    @Test
    @DisplayName("An empty outbox does nothing")
    void shouldDoNothingWhenTheOutboxIsEmpty() {
        when(outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(Page.empty());

        assertThat(publisher.drain()).isZero();
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    private OutboxEvent pendingRow() {
        String notificationId = UUID.randomUUID().toString();
        NotificationCreatedEvent payload = new NotificationCreatedEvent(notificationId, "demo-takwa",
                "TRANSFER_COMPLETED", "TRANSACTIONS", "Transfer completed", "SUCCESS", Instant.now());
        DomainEvent<NotificationCreatedEvent> envelope = DomainEvent.of(
                EventType.NOTIFICATION_CREATED, Topics.NOTIFICATION_CREATED,
                "notification-service", "corr-1", payload);

        OutboxEvent row = new OutboxEvent();
        row.setId(UUID.randomUUID().toString());
        row.setTopic(Topics.NOTIFICATION_CREATED);
        row.setEventKey("demo-takwa");
        row.setDedupeKey(Topics.NOTIFICATION_CREATED + ":" + notificationId);
        row.setPayload(write(envelope));
        row.setCreatedAt(Instant.now());
        return row;
    }

    private String write(DomainEvent<NotificationCreatedEvent> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private SendResult<String, DomainEvent<?>> sendResult(String key) {
        return org.mockito.Mockito.mock(SendResult.class);
    }
}