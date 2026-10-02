package com.finova.account.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.account.service.AccountProjectionService;
import com.finova.common.event.DomainEvent;
import com.finova.common.event.Topics;
import com.finova.common.event.TransactionEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes transfer decisions and maintains the balance read projection.
 * <p>
 * The handler never rethrows: an unrecoverable record must not kill the consumer
 * loop, and the projection is idempotent anyway, so a redelivery is harmless.
 */
@Component
public class TransactionProjectionConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionProjectionConsumer.class);

    private final ObjectMapper objectMapper;
    private final AccountProjectionService accountProjectionService;

    public TransactionProjectionConsumer(ObjectMapper objectMapper,
                                         AccountProjectionService accountProjectionService) {
        this.objectMapper = objectMapper;
        this.accountProjectionService = accountProjectionService;
    }

    @KafkaListener(topics = {Topics.TRANSACTION_COMPLETED, Topics.TRANSACTION_FAILED}, groupId = "account-service")
    public void onTransactionEvent(ConsumerRecord<String, DomainEvent> record) {
        DomainEvent<TransactionEvent> event = null;
        try {
            event = objectMapper.convertValue(record.value(), new TypeReference<DomainEvent<TransactionEvent>>() {
            });
            String correlationId = event.correlationId();
            if (Topics.TRANSACTION_FAILED.equals(record.topic())) {
                accountProjectionService.onTransactionFailed(event.payload());
                return;
            }
            boolean applied = accountProjectionService.applyCompleted(event.payload());
            log.info("transaction.completed projection applied={} transactionId={} correlationId={}",
                applied, transactionId(event), correlationId);
        } catch (Exception ex) {
            log.error("Failed to handle {} offset={} correlationId={}", record.topic(), record.offset(),
                event == null ? "unknown" : event.correlationId(), ex);
        }
    }

    private String transactionId(DomainEvent<TransactionEvent> event) {
        return event.payload() == null ? "unknown" : event.payload().transactionId();
    }
}
