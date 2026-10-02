package com.finova.user.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.common.event.AuditRecordEvent;
import com.finova.common.event.DomainEvent;
import com.finova.common.event.Topics;
import com.finova.user.service.AuditRecorder;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reads the audit events produced by the other services.
 * <p>
 * This is the reason the user-service is the platform's system of record: transaction,
 * account and fraud each publish a fact, and exactly one consumer turns all of them
 * into one ordered, append-only history that an investigator can query across
 * services instead of correlating four logs by timestamp.
 * <p>
 * The handler never rethrows. A record that cannot be deserialised, or one that hits
 * an integrity problem other than a duplicate, is logged and dropped: rethrowing
 * would let a single poison message block the partition and silently stop the audit
 * trail for every later event, which is worse than losing one row.
 */
@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final AuditRecorder auditRecorder;

    public AuditEventConsumer(ObjectMapper objectMapper, AuditRecorder auditRecorder) {
        this.objectMapper = objectMapper;
        this.auditRecorder = auditRecorder;
    }

    @KafkaListener(topics = Topics.AUDIT_RECORDED, groupId = "user-service")
    public void onAuditRecorded(ConsumerRecord<String, DomainEvent> record) {
        String correlationId = record.value() == null ? "unknown" : record.value().correlationId();
        try {
            DomainEvent<AuditRecordEvent> envelope = objectMapper.convertValue(record.value(),
                    new TypeReference<DomainEvent<AuditRecordEvent>>() {
                    });
            AuditRecordEvent payload = envelope.payload();
            if (payload == null) {
                log.warn("Discarding audit event {} with no payload, correlationId={}",
                        envelope.eventId(), correlationId);
                return;
            }
            auditRecorder.recordRemote(envelope.eventId(), payload);
            log.debug("Recorded audit event {} action={} service={} correlationId={}",
                    envelope.eventId(), payload.action(), payload.service(), correlationId);
        } catch (RuntimeException ex) {
            log.error("Discarding unprocessable audit record topic={} partition={} offset={} "
                    + "correlationId={}", record.topic(), record.partition(), record.offset(),
                    correlationId, ex);
        }
    }
}
