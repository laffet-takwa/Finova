package com.finova.notification.service;

import com.finova.notification.domain.OutboxEvent;
import com.finova.notification.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Drains the outbox on a fixed delay.
 * <p>
 * Delivery is per row and failure tolerant, so this never throws: an unreachable
 * broker leaves the rows pending and they are retried on the next tick.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxDispatch outboxDispatch;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository, OutboxDispatch outboxDispatch) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxDispatch = outboxDispatch;
    }

    @Scheduled(fixedDelayString = "${finova.notifications.outbox-interval-ms:2000}")
    public int drain() {
        List<OutboxEvent> pending = outboxEventRepository
                .findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, BATCH_SIZE))
                .getContent();
        int published = 0;
        for (OutboxEvent row : pending) {
            if (outboxDispatch.deliver(row.getId())) {
                published++;
            }
        }
        if (!pending.isEmpty()) {
            log.info("Outbox drain complete pending={} published={}", pending.size(), published);
        }
        return published;
    }
}