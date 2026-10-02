package com.finova.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.common.event.DomainEvent;
import com.finova.common.event.EventType;
import com.finova.common.event.NotificationCreatedEvent;
import com.finova.common.event.Topics;
import com.finova.notification.domain.Notification;
import com.finova.notification.domain.OutboxEvent;
import com.finova.notification.repository.NotificationRepository;
import com.finova.notification.repository.OutboxEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Writes one inbox entry together with the {@code notification.created} event that
 * announces it. The two live in one transaction so the platform can never end up
 * with an entry it did not announce, or an announcement for an entry that is not
 * there.
 */
@Component
public class NotificationWriter {

    public static final String SERVICE = "notification-service";

    private final NotificationRepository notificationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public NotificationWriter(NotificationRepository notificationRepository,
                              OutboxEventRepository outboxEventRepository,
                              ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Persists the draft unless it is already there.
     *
     * @return the stored entry, or empty when the source event had already been
     *         processed and this delivery is a duplicate
     * @throws org.springframework.dao.DataIntegrityViolationException when a
     *         concurrent delivery of the same source event won the race; the caller
     *         treats that exactly like the pre-check hit
     */
    @Transactional
    public Optional<Notification> persistWithOutbox(NotificationDraft draft, Instant now) {
        if (notificationRepository.existsBySourceEventId(draft.sourceEventId())) {
            return Optional.empty();
        }
        Notification notification = toEntity(draft, now);
        // Flushed eagerly so the partial unique index is consulted inside this
        // transaction, instead of at an unpredictable point during commit.
        notificationRepository.saveAndFlush(notification);
        outboxEventRepository.save(toOutboxRow(notification, now));
        return Optional.of(notification);
    }

    private Notification toEntity(NotificationDraft draft, Instant now) {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID().toString());
        notification.setUserId(draft.recipientUserId());
        notification.setType(draft.type());
        notification.setCategory(draft.category());
        notification.setSeverity(draft.severity());
        notification.setTitle(draft.title());
        notification.setMessage(draft.message());
        notification.setTransactionId(draft.transactionId());
        notification.setReference(draft.reference());
        notification.setAmount(draft.amount());
        notification.setCurrency(draft.currency());
        notification.setRead(false);
        notification.setReadAt(null);
        notification.setCreatedAt(now);
        notification.setCorrelationId(draft.correlationId());
        notification.setSourceService(draft.sourceService());
        notification.setSourceEventId(draft.sourceEventId());
        return notification;
    }

    private OutboxEvent toOutboxRow(Notification notification, Instant now) {
        NotificationCreatedEvent payload = new NotificationCreatedEvent(
                notification.getId(),
                notification.getUserId(),
                notification.getType().name(),
                notification.getCategory().value(),
                notification.getTitle(),
                notification.getSeverity().name(),
                notification.getCreatedAt());
        DomainEvent<NotificationCreatedEvent> envelope = DomainEvent.of(
                EventType.NOTIFICATION_CREATED, Topics.NOTIFICATION_CREATED, SERVICE,
                correlationId(notification), payload);

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setId(UUID.randomUUID().toString());
        outboxEvent.setTopic(Topics.NOTIFICATION_CREATED);
        outboxEvent.setEventKey(notification.getUserId());
        outboxEvent.setDedupeKey(Topics.NOTIFICATION_CREATED + ":" + notification.getId());
        outboxEvent.setPayload(write(envelope));
        outboxEvent.setCreatedAt(now);
        return outboxEvent;
    }

    private String write(DomainEvent<NotificationCreatedEvent> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("notification.created could not be serialised", ex);
        }
    }

    private String correlationId(Notification notification) {
        String correlationId = notification.getCorrelationId();
        return correlationId == null || correlationId.isBlank() ? "unknown" : correlationId;
    }
}