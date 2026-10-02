package com.finova.transaction.config;

import com.finova.common.event.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares only the topics this service produces.
 * <p>
 * {@code transaction.completed}, {@code transaction.flagged} and
 * {@code account.opened} are owned by the fraud and account services, and
 * {@code transaction.approved} is the fraud service settlement decision. Single
 * partition with replication factor 1 is the local-development topology; the
 * order-critical topics keep a single partition so per-transaction ordering is
 * guaranteed end to end.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic transactionCreatedTopic() {
        return TopicBuilder.name(Topics.TRANSACTION_CREATED).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic transactionFailedTopic() {
        return TopicBuilder.name(Topics.TRANSACTION_FAILED).partitions(1).replicas(1).build();
    }
}