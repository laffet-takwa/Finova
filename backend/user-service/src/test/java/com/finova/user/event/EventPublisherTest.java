package com.finova.user.event;

import com.finova.common.audit.AuditAction;
import com.finova.common.event.AuditRecordEvent;
import com.finova.common.event.DomainEvent;
import com.finova.common.event.EventType;
import com.finova.common.event.Topics;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * The producer must never be able to fail a request.
 * <p>
 * This is not a theoretical concern: with the default {@code max.block.ms} of 60s, an
 * unreachable broker made {@code POST /api/auth/login} hang for a minute and then
 * answer 500, which took the whole identity service down with the event bus. The audit
 * row is written in the caller's transaction regardless, so the system of record is
 * already safe and the announcement is the only thing at stake.
 */
@ExtendWith(MockitoExtension.class)
class EventPublisherTest {

    @Mock
    private KafkaTemplate<String, DomainEvent<?>> kafkaTemplate;

    @Test
    @DisplayName("An audit event is sent to audit.recorded keyed by its event id")
    void shouldPublishTheAuditEventOnTheAuditTopic() {
        EventPublisher publisher = new EventPublisher(kafkaTemplate);

        publisher.publishAudit(AuditAction.LOGIN_SUCCESS, "user-1", "auth", "user-1",
                "41.226.10.37", "SUCCESS", "Sign-in succeeded", Map.of("device", "Chrome"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<DomainEvent<?>> envelope = ArgumentCaptor.forClass(DomainEvent.class);
        verify(kafkaTemplate).send(eq(Topics.AUDIT_RECORDED), anyString(), envelope.capture());

        DomainEvent<?> event = envelope.getValue();
        assertThat(event.eventType()).isEqualTo(EventType.AUDIT_RECORDED);
        assertThat(event.topic()).isEqualTo(Topics.AUDIT_RECORDED);
        assertThat(event.sourceService()).isEqualTo(EventPublisher.SERVICE);
        assertThat(event.correlationId()).isEqualTo("unknown");
        assertThat(event.payload()).isInstanceOf(AuditRecordEvent.class);

        AuditRecordEvent payload = (AuditRecordEvent) event.payload();
        assertThat(payload.action()).isEqualTo("LOGIN_SUCCESS");
        assertThat(payload.userId()).isEqualTo("user-1");
        assertThat(payload.service()).isEqualTo("user-service");
        assertThat(payload.result()).isEqualTo(AuditRecordEvent.RESULT_SUCCESS);
        assertThat(payload.metadata()).containsEntry("device", "Chrome");
        assertThat(payload.occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("A broker that is down does not fail the request that triggered the event")
    void shouldNotPropagateAFailedSend() {
        doThrow(new KafkaException("Topic audit.recorded not present in metadata after 2000 ms.",
                new TimeoutException("no metadata")))
                .when(kafkaTemplate).send(anyString(), anyString(), any());
        EventPublisher publisher = new EventPublisher(kafkaTemplate);

        assertThatCode(() -> publisher.publishAudit(AuditAction.LOGIN_SUCCESS, "user-1", "auth",
                "user-1", "41.226.10.37", "SUCCESS", "Sign-in succeeded", null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("An unchecked failure from the template is swallowed as well")
    void shouldNotPropagateAnUnexpectedProducerFailure() {
        doThrow(new IllegalStateException("producer closed"))
                .when(kafkaTemplate).send(anyString(), anyString(), any());
        EventPublisher publisher = new EventPublisher(kafkaTemplate);

        assertThatCode(() -> publisher.publish(Topics.AUDIT_RECORDED, EventType.AUDIT_RECORDED,
                Map.of("action", "LOGOUT")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("The event carries an occurred-at instant so a slow broker cannot rewrite history")
    void shouldStampTheEventAtPublishTime() {
        EventPublisher publisher = new EventPublisher(kafkaTemplate);
        Instant before = Instant.now().minusSeconds(1);

        publisher.publishAudit(AuditAction.LOGOUT, "user-1", "auth", "user-1", null, "SUCCESS",
                "Session ended", null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<DomainEvent<?>> envelope = ArgumentCaptor.forClass(DomainEvent.class);
        verify(kafkaTemplate).send(anyString(), anyString(), envelope.capture());
        AuditRecordEvent payload = (AuditRecordEvent) envelope.getValue().payload();
        assertThat(payload.occurredAt()).isAfterOrEqualTo(before);
    }
}