package com.finova.user.service;

import com.finova.common.audit.AuditAction;
import com.finova.common.event.AuditRecordEvent;
import com.finova.user.domain.AuditLog;
import com.finova.user.event.EventPublisher;
import com.finova.user.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The single writer of the audit trail.
 * <p>
 * Two directions arrive here. A request handled by this service writes a row inside
 * its own business transaction and then publishes the same fact to
 * {@code audit.recorded}. An event published by another service is read back by the
 * consumer and written here.
 * <p>
 * The insert is deliberately not wrapped in a transaction of its own. When it runs on
 * the request path it joins the caller's unit of work, so the record and the state
 * change it describes commit together. When it runs on the consumer path there is no
 * ambient transaction, so a duplicate rejected by the partial unique index rolls back
 * cleanly and can be caught here — which is what makes at-least-once delivery safe.
 */
@Component
public class AuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);
    private static final String UNKNOWN = "unknown";

    private final AuditLogRepository auditLogRepository;
    private final EventPublisher eventPublisher;
    private final RequestContext requestContext;

    public AuditRecorder(AuditLogRepository auditLogRepository, EventPublisher eventPublisher,
                         RequestContext requestContext) {
        this.auditLogRepository = auditLogRepository;
        this.eventPublisher = eventPublisher;
        this.requestContext = requestContext;
    }

    /**
     * Records an operation this service just performed and announces it to the rest
     * of the platform. The caller owns the transaction.
     */
    public void record(AuditAction action, String userId, String resource, String resourceId,
                       String result, String message, Map<String, String> metadata) {
        AuditLog entry = newEntry(action, userId, resource, resourceId, result, message, metadata, null);
        persist(entry);
        eventPublisher.publishAudit(action, userId, resource, resourceId, entry.getIpAddress(),
                result, message, metadata);
    }

    /**
     * Records an audit event produced by another service.
     * <p>
     * Idempotent on {@code eventId}: a Kafka redelivery of the same envelope writes
     * nothing. The existence check is an optimisation, not the guarantee — two
     * consumer threads can pass it simultaneously, and the partial unique index is
     * what actually stops the second insert.
     */
    public void recordRemote(String eventId, AuditRecordEvent event) {
        if (eventId != null && auditLogRepository.existsByEventId(eventId)) {
            log.debug("Audit event {} already recorded, skipping", eventId);
            return;
        }
        AuditLog entry = new AuditLog();
        entry.setId(UUID.randomUUID().toString());
        entry.setEventId(eventId);
        entry.setAction(truncate(event.action(), 40));
        entry.setUserId(trimToNull(event.userId()));
        entry.setResource(truncate(orUnknown(event.resource()), 60));
        entry.setResourceId(trimToNull(event.resourceId()));
        entry.setIpAddress(trimToNull(event.ipAddress()));
        entry.setCorrelationId(trimToNull(event.correlationId()));
        entry.setResult(truncate(orUnknown(event.result()), 20));
        entry.setService(truncate(orUnknown(event.service()), 40));
        entry.setMessage(trimToNull(event.message()));
        entry.setMetadata(event.metadata());
        entry.setCreatedAt(event.occurredAt() == null ? Instant.now() : event.occurredAt());
        persistQuietly(entry, eventId);
    }

    private AuditLog newEntry(AuditAction action, String userId, String resource, String resourceId,
                              String result, String message, Map<String, String> metadata, String eventId) {
        AuditLog entry = new AuditLog();
        entry.setId(UUID.randomUUID().toString());
        entry.setEventId(eventId);
        entry.setAction(action.name());
        entry.setUserId(trimToNull(userId));
        entry.setResource(truncate(orUnknown(resource), 60));
        entry.setResourceId(truncate(trimToNull(resourceId), 80));
        entry.setIpAddress(requestContext.ipAddress());
        entry.setCorrelationId(truncate(requestContext.correlationId(), 64));
        entry.setResult(truncate(orUnknown(result), 20));
        entry.setService(EventPublisher.SERVICE);
        entry.setMessage(trimToNull(message));
        entry.setMetadata(metadata);
        entry.setCreatedAt(Instant.now());
        return entry;
    }

    private void persist(AuditLog entry) {
        auditLogRepository.save(entry);
    }

    /**
     * Swallows the duplicate-key rejection of a redelivered event. Any other
     * integrity problem is a real defect and is left to propagate.
     */
    private void persistQuietly(AuditLog entry, String eventId) {
        try {
            auditLogRepository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateEvent(ex)) {
                log.debug("Audit event {} raced the existence check and was rejected by the unique index",
                        eventId);
                return;
            }
            throw ex;
        }
    }

    private boolean isDuplicateEvent(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause() == null ? "" : ex.getMostSpecificCause().getMessage();
        return message != null && message.contains("uq_audit_logs_event_id");
    }

    private String orUnknown(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
