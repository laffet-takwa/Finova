package com.finova.transaction.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.common.event.AccountOpenedEvent;
import com.finova.common.event.DomainEvent;
import com.finova.common.error.BusinessException;
import com.finova.common.event.Topics;
import com.finova.transaction.service.LedgerProjectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Creates and refreshes the ledger projection when the account service opens an
 * account or restates its opening balance, so the ledger is populated without a
 * synchronous dependency in the other direction.
 */
@Component
public class AccountOpenedConsumer {

    private static final Logger log = LoggerFactory.getLogger(AccountOpenedConsumer.class);
    private static final TypeReference<DomainEvent<AccountOpenedEvent>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final LedgerProjectionService projections;

    public AccountOpenedConsumer(ObjectMapper objectMapper, LedgerProjectionService projections) {
        this.objectMapper = objectMapper;
        this.projections = projections;
    }

    @KafkaListener(topics = Topics.ACCOUNT_OPENED, groupId = "transaction-service-ledger-projection")
    public void onAccountOpened(String raw) {
        try {
            DomainEvent<AccountOpenedEvent> envelope = objectMapper.readValue(raw, PAYLOAD_TYPE);
            if (envelope.payload() == null) {
                log.warn("Ignoring account.opened without payload eventId={}", envelope.eventId());
                return;
            }
            projections.onAccountOpened(envelope.payload());
        } catch (BusinessException ex) {
            log.warn("Ledger projection rejected code={} reason={}", ex.getErrorCode(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Ledger projection could not be created: {}", ex.getMessage(), ex);
        }
    }
}