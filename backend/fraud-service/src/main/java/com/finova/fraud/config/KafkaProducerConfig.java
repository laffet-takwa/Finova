package com.finova.fraud.config;

import com.finova.common.event.DomainEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Typed producer wiring.
 * <p>
 * Spring Boot only auto-configures {@code KafkaTemplate<Object, Object>}. Declaring the typed
 * template explicitly lets Jackson resolve {@code DomainEvent<?>} when serialising, which the
 * {@code JsonSerializer} needs to write the payload without a type hint.
 * <p>
 * This deliberately lives outside the application class: a web or Mongo slice test has no
 * {@code ProducerFactory}, and the slice filters exclude plain configuration classes, so those
 * slices start without Kafka at all.
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