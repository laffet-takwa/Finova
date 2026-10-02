package com.finova.account.config;

import com.finova.common.event.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic accountOpenedTopic() {
        return TopicBuilder.name(Topics.ACCOUNT_OPENED).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic accountBlockedTopic() {
        return TopicBuilder.name(Topics.ACCOUNT_BLOCKED).partitions(1).replicas(1).build();
    }
}
