package com.finova.transaction.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.finova.common.event.DomainEvent;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Kafka producer wiring.
 * <p>
 * The template is declared with an explicit {@code DomainEvent<?>} value type so
 * the generic payload resolves on injection instead of degrading to
 * {@code Object}; it reuses the auto-configured {@code ProducerFactory}, so
 * {@code spring.kafka.producer.*} stays the single source of producer settings.
 */
@Configuration
public class KafkaProducerConfig {

    @Bean
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, DomainEvent<?>> kafkaTemplate(ProducerFactory<Object, Object> producerFactory) {
        return new KafkaTemplate<>((ProducerFactory<String, DomainEvent<?>>) (ProducerFactory<?, ?>) producerFactory);
    }

    /**
     * Inbound settlement decisions can carry event types this build does not know
     * yet, for example {@code TRANSACTION_APPROVED} before it lands in
     * {@code EventType}. Handlers dispatch on topic and payload, never on the
     * envelope type, so an unknown type must deserialise to null rather than kill
     * the consumer loop.
     */
    @Bean
    Jackson2ObjectMapperBuilderCustomizer inboundEventMapperCustomizer() {
        return builder -> builder.postConfigurer(mapper ->
                mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true));
    }
}