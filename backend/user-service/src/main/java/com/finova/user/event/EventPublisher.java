package com.finova.user.event;

import com.finova.common.audit.AuditAction;
import com.finova.common.event.AuditRecordEvent;
import com.finova.common.event.DomainEvent;
import com.finova.common.event.EventType;
import com.finova.common.event.Topics;
import com.finova.common.web.CorrelationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * The only place this service writes to Kafka.
 * <p>
 * The audit trail is published so that anything downstream of a sign-in — a fraud
 * review, an incident investigation — can follow the same envelope the user-service
 * itself consumes. The row is written in the same database transaction as the state
 * change it describes, so a broker outage delays the event but never loses the
 * record.
 */
@Component
public class EventPublisher {

    public static final String SERVICE = "user-service";

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final KafkaTemplate<String, DomainEvent<?>> kafkaTemplate;

    public EventPublisher(KafkaTemplate<String, DomainEvent<?>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Announces a fact to the rest of the platform, and never fails the caller.
     * <p>
     * This runs on the request thread, inside the caller's transaction, so an
     * unavailable broker would otherwise turn every sign-in and every registration
     * into a 500 and hold the HTTP thread for {@code max.block.ms} first: the
     * identity service must not be taken down by the event bus. The audit row itself
     * has already been written in the same transaction, so the system of record is
     * unaffected; what is lost is only the notification to downstream readers, and
     * that is logged loudly with the correlation id instead of being hidden.
     */
    public <T> void publish(String topic, EventType type, T payload) {
        DomainEvent<T> event = DomainEvent.of(type, topic, SERVICE, correlationId(), payload);
        String correlationId = event.correlationId();
        try {
            kafkaTemplate.send(topic, event.eventId(), event);
        } catch (RuntimeException ex) {
            log.warn("Could not publish {} to {}; the audit row is written, the "
                    + "announcement is not. correlationId={} cause={}", type, topic, correlationId,
                    ex.getMessage());
        }
    }

    public void publishAudit(AuditAction action, String userId, String resource, String resourceId,
                             String ipAddress, String result, String message,
                             Map<String, String> metadata) {
        AuditRecordEvent payload = new AuditRecordEvent(action.name(), userId, resource, resourceId,
                ipAddress, correlationId(), result, SERVICE, message, metadata, Instant.now());
        publish(Topics.AUDIT_RECORDED, EventType.AUDIT_RECORDED, payload);
    }

    private String correlationId() {
        String correlationId = MDC.get(CorrelationId.MDC_KEY);
        return correlationId == null ? "unknown" : correlationId;
    }
}
