package com.finova.notification.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.common.event.AccountBlockedEvent;
import com.finova.common.event.DomainEvent;
import com.finova.common.event.Topics;
import com.finova.notification.service.NotificationIngestionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Turns {@code account.blocked} into a single inbox entry for the account holder.
 * <p>
 * As with the transfer topics, the handler never rethrows: a record it cannot make
 * sense of is logged and dropped rather than allowed to stall the partition.
 */
@Component
public class AccountNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(AccountNotificationConsumer.class);

    private final ObjectMapper objectMapper;
    private final NotificationIngestionService ingestionService;

    public AccountNotificationConsumer(ObjectMapper objectMapper,
                                       NotificationIngestionService ingestionService) {
        this.objectMapper = objectMapper;
        this.ingestionService = ingestionService;
    }

    @KafkaListener(topics = Topics.ACCOUNT_BLOCKED, groupId = "notification-service")
    public void onAccountBlocked(ConsumerRecord<String, DomainEvent> record) {
        String correlationId = record.value() == null ? "unknown" : record.value().correlationId();
        try {
            DomainEvent<AccountBlockedEvent> envelope = objectMapper.convertValue(record.value(),
                    new TypeReference<DomainEvent<AccountBlockedEvent>>() {
                    });
            ingestionService.handleAccountBlocked(envelope);
            log.debug("Processed {} eventId={} correlationId={}", record.topic(),
                    envelope.eventId(), correlationId);
        } catch (RuntimeException ex) {
            log.error("Discarding unprocessable record topic={} partition={} offset={} correlationId={}",
                    record.topic(), record.partition(), record.offset(), correlationId, ex);
        }
    }
}