package com.finova.transaction.messaging;

import com.finova.common.event.DomainEvent;
import com.finova.transaction.config.TransactionProperties;
import com.finova.transaction.domain.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Drains the transactional outbox to Kafka.
 * <p>
 * The drainer is deliberately independent of any business transaction: it reads
 * unpublished rows, sends them and then stamps them in their own short
 * transaction. A broker outage therefore only delays delivery - the rows stay in
 * the table and are retried, so no event is lost.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final long SEND_TIMEOUT_SECONDS = 10L;

    private final OutboxStore store;
    private final KafkaTemplate<String, DomainEvent<?>> kafkaTemplate;

    public OutboxPublisher(OutboxStore store,
                           KafkaTemplate<String, DomainEvent<?>> kafkaTemplate,
                           TransactionProperties properties) {
        this.store = store;
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = properties.getOutboxBatchSize();
        this.maxAttempts = properties.getOutboxMaxAttempts();
    }

    private final int batchSize;
    private final int maxAttempts;

    @Scheduled(fixedDelayString = "${finova.transactions.outbox-interval-ms:500}")
    public void drain() {
        List<OutboxEvent> pending = store.findUnpublished(batchSize);
        if (pending.isEmpty()) {
            return;
        }
        log.debug("Draining {} outbox event(s)", pending.size());
        for (OutboxEvent event : pending) {
            publish(event);
        }
    }

    private void publish(OutboxEvent event) {
        try {
            DomainEvent<?> envelope = store.readEnvelope(event);
            CompletableFuture<SendResult<String, DomainEvent<?>>> future =
                    kafkaTemplate.send(event.getTopic(), event.getEventKey(), envelope);
            future.get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            store.markPublished(event.getId());
        } catch (Exception ex) {
            log.warn("Outbox publish failed topic={} eventKey={} reason={}",
                    event.getTopic(), event.getEventKey(), ex.getMessage());
            store.markFailed(event.getId(), maxAttempts);
        }
    }
}