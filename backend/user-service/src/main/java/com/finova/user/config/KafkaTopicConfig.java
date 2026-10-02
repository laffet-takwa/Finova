package com.finova.user.config;

import com.finova.common.event.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topics this service owns.
 * <p>
 * Only {@code audit.recorded} is declared: it is the one topic this service both
 * produces to and consumes from. The other topics are created by their producers,
 * and redeclaring them here would fight the owner over the partition count.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic auditRecordedTopic() {
        return TopicBuilder.name(Topics.AUDIT_RECORDED)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
