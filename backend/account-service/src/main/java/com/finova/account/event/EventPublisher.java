package com.finova.account.event;

import com.finova.common.audit.AuditAction;
import com.finova.common.event.AuditRecordEvent;
import com.finova.common.event.DomainEvent;
import com.finova.common.event.EventType;
import com.finova.common.event.Topics;
import com.finova.common.web.CorrelationId;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class EventPublisher {

    public static final String SERVICE = "account-service";

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public EventPublisher(KafkaTemplate<Object, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public <T> void publish(String topic, EventType type, T payload) {
        DomainEvent<T> event = DomainEvent.of(type, topic, SERVICE, correlationId(), payload);
        kafkaTemplate.send(topic, event.eventId(), event);
    }

    public void publishAudit(AuditAction action, String userId, String resource, String resourceId,
                             String ipAddress, String result, String message, Map<String, String> metadata) {
        AuditRecordEvent payload = new AuditRecordEvent(action.name(), userId, resource, resourceId,
            ipAddress, correlationId(), result, SERVICE, message, metadata, Instant.now());
        publish(Topics.AUDIT_RECORDED, EventType.AUDIT_RECORDED, payload);
    }

    private String correlationId() {
        String correlationId = MDC.get(CorrelationId.MDC_KEY);
        return correlationId == null ? "unknown" : correlationId;
    }
}
