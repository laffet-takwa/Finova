package com.finova.notification.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.common.event.DomainEvent;
import com.finova.common.event.Topics;
import com.finova.common.event.TransactionEvent;
import com.finova.notification.service.NotificationIngestionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Terminal facts of the transfer flow.
 * <p>
 * {@code transaction.approved} is deliberately absent from this list. It carries the
 * fraud decision to proceed; the money has not moved at that point, and a customer
 * told "Transfer completed" on an approval would be told a lie. The inbox is driven
 * by {@code transaction.completed}, {@code transaction.failed} and
 * {@code transaction.flagged} only.
 * <p>
 * The handler never rethrows. A poison message that could not be deserialised or
 * stored must not take the consumer loop down and stop every later transfer from
 * being reported.
 */
@Component
public class TransactionNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionNotificationConsumer.class);

    private final ObjectMapper objectMapper;
    private final NotificationIngestionService ingestionService;

    public TransactionNotificationConsumer(ObjectMapper objectMapper,
                                           NotificationIngestionService ingestionService) {
        this.objectMapper = objectMapper;
        this.ingestionService = ingestionService;
    }

    @KafkaListener(topics = {
            Topics.TRANSACTION_COMPLETED,
            Topics.TRANSACTION_FAILED,
            Topics.TRANSACTION_FLAGGED
    }, groupId = "notification-service")
    public void onTransactionEvent(ConsumerRecord<String, DomainEvent> record) {
        String correlationId = record.value() == null ? "unknown" : record.value().correlationId();
        try {
            DomainEvent<TransactionEvent> envelope = objectMapper.convertValue(record.value(),
                    new TypeReference<DomainEvent<TransactionEvent>>() {
                    });
            ingestionService.handleTransactionEvent(record.topic(), envelope);
            log.debug("Processed {} eventId={} correlationId={}", record.topic(),
                    envelope.eventId(), correlationId);
        } catch (RuntimeException ex) {
            log.error("Discarding unprocessable record topic={} partition={} offset={} correlationId={}",
                    record.topic(), record.partition(), record.offset(), correlationId, ex);
        }
    }
}