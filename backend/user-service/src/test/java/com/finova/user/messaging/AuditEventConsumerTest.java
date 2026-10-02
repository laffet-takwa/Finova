package com.finova.user.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finova.common.event.AuditRecordEvent;
import com.finova.common.event.DomainEvent;
import com.finova.common.event.Topics;
import com.finova.user.domain.AuditLog;
import com.finova.user.repository.AuditLogRepository;
import com.finova.user.service.AuditRecorder;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The audit consumer, which is what makes this service the platform's system of
 * record. The recorder is the real one and persistence is mocked, so the assertions
 * are about the two things that actually matter: what gets stored, and what happens
 * when Kafka delivers the same event twice.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditEventConsumerTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    /**
     * Mirrors the mapper Spring Boot injects in production, including
     * {@code FAIL_ON_UNKNOWN_PROPERTIES} off: {@code DomainEvent} is a record whose
     * helper methods Jackson also serialises, so the round trip has to tolerate the
     * extra properties.
     */
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /** Stands in for the partial unique index on {@code event_id}. */
    private final Set<String> storedEventIds = new LinkedHashSet<>();
    private final List<AuditLog> stored = new ArrayList<>();

    private AuditEventConsumer consumer;

    @BeforeEach
    void setUp() {
        when(auditLogRepository.existsByEventId(anyString()))
                .thenAnswer(invocation -> storedEventIds.contains(invocation.getArgument(0)));
        when(auditLogRepository.saveAndFlush(any(AuditLog.class))).thenAnswer(invocation -> {
            AuditLog entry = invocation.getArgument(0);
            storedEventIds.add(entry.getEventId());
            stored.add(entry);
            return entry;
        });
        consumer = new AuditEventConsumer(objectMapper, new AuditRecorder(auditLogRepository,
                null, null));
    }

    @Test
    @DisplayName("The same event delivered twice produces exactly one row")
    void shouldRecordAnEventOnlyOnceWhenItIsRedelivered() {
        String eventId = "b1d0a3f6-2f1e-4a0f-9a1b-2c3d4e5f6a7b";

        consumer.onAuditRecorded(record(eventId, "TRANSFER_COMPLETED", "transaction-service"));
        consumer.onAuditRecorded(record(eventId, "TRANSFER_COMPLETED", "transaction-service"));
        consumer.onAuditRecorded(record(eventId, "TRANSFER_COMPLETED", "transaction-service"));

        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getEventId()).isEqualTo(eventId);
        assertThat(stored.get(0).getAction()).isEqualTo("TRANSFER_COMPLETED");
    }

    @Test
    @DisplayName("A delivery that races the existence check is swallowed by the unique index")
    void shouldIgnoreADeliveryRejectedByTheUniqueIndex() {
        when(auditLogRepository.existsByEventId(anyString())).thenReturn(false);
        when(auditLogRepository.saveAndFlush(any(AuditLog.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "uq_audit_logs_event_id violates not-null constraint"));

        consumer.onAuditRecorded(record("evt-race", "ACCOUNT_BLOCKED", "account-service"));

        assertThat(stored).isEmpty();
    }

    @Test
    @DisplayName("Every field of the payload is carried across, including the JSONB metadata")
    void shouldCarryEveryPayloadFieldIntoTheStoredRow() {
        Instant occurredAt = Instant.parse("2026-09-18T21:04:11Z");
        AuditRecordEvent payload = new AuditRecordEvent("FRAUD_DETECTED",
                "3f6d9a1c-4b7e-4f0a-9c2d-8e5f1a2b3c4d", "fraud-alert", "FA-20260918-00003",
                "41.226.10.37", "corr-9f1c", AuditRecordEvent.RESULT_FAILURE, "fraud-service",
                "Transfer held for manual review after risk analysis",
                Map.of("riskScore", "82", "riskLevel", "HIGH"), occurredAt);

        consumer.onAuditRecorded(new ConsumerRecord<>(Topics.AUDIT_RECORDED, 0, 7L, "key",
                envelope("evt-fraud-1", payload)));

        AuditLog entry = stored.get(0);
        assertThat(entry.getEventId()).isEqualTo("evt-fraud-1");
        assertThat(entry.getAction()).isEqualTo("FRAUD_DETECTED");
        assertThat(entry.getUserId()).isEqualTo("3f6d9a1c-4b7e-4f0a-9c2d-8e5f1a2b3c4d");
        assertThat(entry.getResource()).isEqualTo("fraud-alert");
        assertThat(entry.getResourceId()).isEqualTo("FA-20260918-00003");
        assertThat(entry.getIpAddress()).isEqualTo("41.226.10.37");
        assertThat(entry.getCorrelationId()).isEqualTo("corr-9f1c");
        assertThat(entry.getResult()).isEqualTo("FAILURE");
        assertThat(entry.getService()).isEqualTo("fraud-service");
        assertThat(entry.getMessage()).isEqualTo("Transfer held for manual review after risk analysis");
        assertThat(entry.getMetadata()).containsEntry("riskScore", "82").containsEntry("riskLevel", "HIGH");
        assertThat(entry.getCreatedAt()).isEqualTo(occurredAt);
    }

    @Test
    @DisplayName("Two different events are both recorded")
    void shouldRecordDistinctEvents() {
        consumer.onAuditRecorded(record("evt-a", "TRANSFER_CREATED", "transaction-service"));
        consumer.onAuditRecorded(record("evt-b", "TRANSFER_COMPLETED", "transaction-service"));

        assertThat(stored).hasSize(2);
        assertThat(stored).extracting(AuditLog::getAction)
                .containsExactly("TRANSFER_CREATED", "TRANSFER_COMPLETED");
    }

    @Test
    @DisplayName("A poison record is logged and never propagates out of the listener")
    void shouldNotPropagateWhenTheRecordCannotBeRead() {
        DomainEvent<String> malformed = new DomainEvent<>("evt-broken", null,
                Topics.AUDIT_RECORDED, Instant.now(), "corr-broken", "account-service",
                "this payload is not an audit record");

        consumer.onAuditRecorded(new ConsumerRecord<>(Topics.AUDIT_RECORDED, 0, 12L, "key", malformed));

        assertThat(stored).isEmpty();
        verify(auditLogRepository, never()).saveAndFlush(any(AuditLog.class));
    }

    @Test
    @DisplayName("An envelope with no payload is dropped without failing the partition")
    void shouldIgnoreAnEnvelopeWithoutAPayload() {
        consumer.onAuditRecorded(new ConsumerRecord<>(Topics.AUDIT_RECORDED, 0, 3L, "key",
                new DomainEvent<>("evt-empty", null, Topics.AUDIT_RECORDED, Instant.now(),
                        "corr-empty", "user-service", null)));

        assertThat(stored).isEmpty();
    }

    private ConsumerRecord<String, DomainEvent> record(String eventId, String action, String service) {
        AuditRecordEvent payload = new AuditRecordEvent(action, null, "transaction", null,
                "41.226.10.37", "corr-" + eventId, AuditRecordEvent.RESULT_SUCCESS, service,
                "Recorded by " + service, Map.of("channel", "web"), Instant.now());
        return new ConsumerRecord<>(Topics.AUDIT_RECORDED, 0, 1L, eventId, envelope(eventId, payload));
    }

    private DomainEvent<AuditRecordEvent> envelope(String eventId, AuditRecordEvent payload) {
        return new DomainEvent<>(eventId, null, Topics.AUDIT_RECORDED, Instant.now(),
                "corr-" + eventId, "transaction-service", payload);
    }

    @Test
    @DisplayName("The recorder never writes a row without an event id on the consumer path")
    void shouldAlwaysSetTheEventIdOnAConsumerWrite() {
        consumer.onAuditRecorded(record("evt-id-check", "LOGIN_SUCCESS", "user-service"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo("evt-id-check");
    }
}
