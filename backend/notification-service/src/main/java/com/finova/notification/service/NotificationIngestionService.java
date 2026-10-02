package com.finova.notification.service;

import com.finova.common.event.AccountBlockedEvent;
import com.finova.common.event.DomainEvent;
import com.finova.common.event.TransactionEvent;
import com.finova.notification.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry point of the write path: reads an event, asks the factory what the
 * recipients should be told, and asks the {@link NotificationWriter} to store it.
 * <p>
 * Intentionally not transactional itself. Each entry is stored in its own
 * transaction, so a duplicate rejected on the second of two entries cannot roll
 * back the first one, and a {@link DataIntegrityViolationException} raised by the
 * partial unique index arrives here after the failing transaction has already been
 * rolled back and is therefore safe to swallow.
 */
@Service
public class NotificationIngestionService {

    private static final Logger log = LoggerFactory.getLogger(NotificationIngestionService.class);

    private final NotificationFactory notificationFactory;
    private final NotificationWriter notificationWriter;

    public NotificationIngestionService(NotificationFactory notificationFactory,
                                        NotificationWriter notificationWriter) {
        this.notificationFactory = notificationFactory;
        this.notificationWriter = notificationWriter;
    }

    public List<Notification> handleTransactionEvent(String topic, DomainEvent<TransactionEvent> envelope) {
        return store(notificationFactory.fromTransaction(topic, envelope), envelope == null ? null : envelope.correlationId());
    }

    public List<Notification> handleAccountBlocked(DomainEvent<AccountBlockedEvent> envelope) {
        return store(notificationFactory.fromAccountBlocked(envelope), envelope == null ? null : envelope.correlationId());
    }

    private List<Notification> store(List<NotificationDraft> drafts, String correlationId) {
        List<Notification> stored = new ArrayList<>(drafts.size());
        for (NotificationDraft draft : drafts) {
            try {
                notificationWriter.persistWithOutbox(draft, Instant.now()).ifPresent(stored::add);
            } catch (DataIntegrityViolationException ex) {
                // The partial unique index on source_event_id rejected a delivery that
                // raced the pre-check. Same outcome as the pre-check hit: skip.
                log.info("Duplicate notification delivery ignored correlationId={} sourceEventId={}",
                        correlationId, draft.sourceEventId());
            } catch (RuntimeException ex) {
                log.error("Could not store notification correlationId={} sourceEventId={} type={}",
                        correlationId, draft.sourceEventId(), draft.type(), ex);
            }
        }
        return stored;
    }
}