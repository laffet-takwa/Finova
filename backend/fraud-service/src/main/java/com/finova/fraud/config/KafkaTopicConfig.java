package com.finova.fraud.config;

import com.finova.common.event.Topics;
import com.finova.fraud.event.FraudTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topics this service owns. Everything else it publishes is owned, and therefore created, by the
 * producing service; creating those here would fight the owner over partition counts.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic transactionFlaggedTopic() {
        return TopicBuilder.name(Topics.TRANSACTION_FLAGGED)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic transactionApprovedTopic() {
        return TopicBuilder.name(FraudTopics.TRANSACTION_APPROVED)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic accountBlockedTopic() {
        return TopicBuilder.name(Topics.ACCOUNT_BLOCKED)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic auditRecordedTopic() {
        return TopicBuilder.name(Topics.AUDIT_RECORDED)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
