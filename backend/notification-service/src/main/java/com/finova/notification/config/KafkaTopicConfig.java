package com.finova.notification.config;

import com.finova.common.event.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topics this service owns.
 * <p>
 * Only {@code notification.created} is declared: the topics this service consumes
 * are created by their producers, and redeclaring them here would fight the owner
 * over the partition count.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic notificationCreatedTopic() {
        return TopicBuilder.name(Topics.NOTIFICATION_CREATED)
                .partitions(1)
                .replicas(1)
                .build();
    }
}