package com.finova.notification.config;

import com.finova.common.event.DomainEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Typed producer wiring.
 * <p>
 * Spring Boot only auto-configures {@code KafkaTemplate<Object, Object>}. The typed
 * template is declared explicitly so the {@code JsonSerializer} serialises the
 * {@code DomainEvent} envelope with a resolved generic payload instead of failing
 * on the type variable.
 */
@Configuration
public class KafkaProducerConfig {

    @Bean
    @SuppressWarnings("unchecked")
    public KafkaTemplate<String, DomainEvent<?>> domainEventTemplate(
            ProducerFactory<Object, Object> producerFactory) {
        return new KafkaTemplate<>((ProducerFactory<String, DomainEvent<?>>) (ProducerFactory<?, ?>)
                producerFactory);
    }
}