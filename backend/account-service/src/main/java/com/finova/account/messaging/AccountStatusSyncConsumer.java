package com.finova.account.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.account.service.AccountStatusSyncService;
import com.finova.common.event.AccountBlockedEvent;
import com.finova.common.event.DomainEvent;
import com.finova.common.event.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * EVENT-FLOW.md lists account-service as a consumer of {@code account.blocked}:
 * the fraud-service may block an account on its own, without going through
 * {@code PUT /api/accounts/{id}/status}, and the local product must follow.
 */
@Component
public class AccountStatusSyncConsumer {

    private static final Logger log = LoggerFactory.getLogger(AccountStatusSyncConsumer.class);

    private final ObjectMapper objectMapper;
    private final AccountStatusSyncService accountStatusSyncService;

    public AccountStatusSyncConsumer(ObjectMapper objectMapper,
                                     AccountStatusSyncService accountStatusSyncService) {
        this.objectMapper = objectMapper;
        this.accountStatusSyncService = accountStatusSyncService;
    }

    @KafkaListener(topics = Topics.ACCOUNT_BLOCKED, groupId = "account-service")
    public void onAccountBlocked(ConsumerRecord<String, DomainEvent> record) {
        DomainEvent<AccountBlockedEvent> event = null;
        try {
            event = objectMapper.convertValue(record.value(), new TypeReference<DomainEvent<AccountBlockedEvent>>() {
            });
            boolean applied = accountStatusSyncService.blockLocally(event.payload());
            log.info("account.blocked sync applied={} accountId={} correlationId={}",
                applied, event.payload() == null ? null : event.payload().accountId(), event.correlationId());
        } catch (Exception ex) {
            log.error("Failed to sync account.blocked offset={} correlationId={}", record.offset(),
                event == null ? "unknown" : event.correlationId(), ex);
        }
    }
}