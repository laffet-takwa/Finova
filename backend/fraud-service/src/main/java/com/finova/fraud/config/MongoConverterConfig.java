package com.finova.fraud.config;

import org.bson.types.Decimal128;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.math.BigDecimal;
import java.util.List;

/**
 * Money is stored as BSON {@code Decimal128}, never as a double or a plain string.
 * <p>
 * The velocity window has to add to {@code totalAmount} with {@code $inc} inside MongoDB itself, so
 * the storage type is pinned here rather than left to chance: a double would silently lose millimes
 * on a running total. The list constructor is used deliberately, because it adds to the store
 * converters rather than replacing them, which keeps the JSR-310 handling for {@code Instant}.
 */
@Configuration
public class MongoConverterConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(
                BigDecimalToDecimal128.INSTANCE, Decimal128ToBigDecimal.INSTANCE));
    }

    @WritingConverter
    public enum BigDecimalToDecimal128 implements Converter<BigDecimal, Decimal128> {
        INSTANCE;

        @Override
        public Decimal128 convert(BigDecimal source) {
            return source == null ? null : new Decimal128(source);
        }
    }

    @ReadingConverter
    public enum Decimal128ToBigDecimal implements Converter<Decimal128, BigDecimal> {
        INSTANCE;

        @Override
        public BigDecimal convert(Decimal128 source) {
            return source == null ? null : source.bigDecimalValue();
        }
    }
}
