package com.finova.notification.event;

import com.finova.common.event.DomainEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * The only place this service writes to Kafka.
 * <p>
 * Messages are published from the outbox with the envelope that was stored with
 * them, so the {@code eventId} a downstream consumer sees is the one that was
 * minted when the inbox row was written, and stays stable across retries.
 */
@Component
public class NotificationEventPublisher {

    private final KafkaTemplate<String, DomainEvent<?>> kafkaTemplate;

    public NotificationEventPublisher(KafkaTemplate<String, DomainEvent<?>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<SendResult<String, DomainEvent<?>>> publish(String topic, String key,
                                                                           DomainEvent<?> envelope) {
        return kafkaTemplate.send(topic, key, envelope);
    }
}