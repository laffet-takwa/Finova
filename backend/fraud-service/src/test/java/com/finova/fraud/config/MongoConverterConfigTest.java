package com.finova.fraud.config;

import org.bson.types.Decimal128;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Money must land in MongoDB as {@code Decimal128}: the velocity window adds to {@code totalAmount}
 * with {@code $inc} inside the database, and a double would quietly drop millimes from a running
 * total on every increment.
 * <p>
 * The live proof is {@code VelocityWindowTest} against a real MongoDB; these assertions pin the
 * conversion itself so a regression surfaces without Docker.
 */
@DisplayName("Mongo money conversion")
class MongoConverterConfigTest {

    private final MongoConverterConfig config = new MongoConverterConfig();
    private final MongoCustomConversions conversions = config.mongoCustomConversions();

    @Test
    void shouldRegisterDecimal128AsTheWriteTargetForMoney() {
        assertThat(conversions.hasCustomWriteTarget(BigDecimal.class)).isTrue();
        assertThat(conversions.hasCustomWriteTarget(BigDecimal.class, Decimal128.class)).isTrue();
    }

    @Test
    void shouldRegisterBigDecimalAsTheReadTargetForDecimal128() {
        assertThat(conversions.hasCustomReadTarget(Decimal128.class, BigDecimal.class)).isTrue();
    }

    @Test
    void shouldRoundTripMoneyWithoutLosingMillimes() {
        BigDecimal amount = new BigDecimal("3571.428");

        Decimal128 stored = MongoConverterConfig.BigDecimalToDecimal128.INSTANCE.convert(amount);
        BigDecimal read = MongoConverterConfig.Decimal128ToBigDecimal.INSTANCE.convert(stored);

        assertThat(stored.toString()).isEqualTo("3571.428");
        assertThat(read).isEqualByComparingTo("3571.428");
        assertThat(read.scale()).isEqualTo(3);
    }

    @Test
    void shouldKeepTheThreeDecimalsOfEveryEngineAmount() {
        for (String raw : new String[]{"15000.000", "12450.750", "9000.000", "0.001"}) {
            Decimal128 stored = MongoConverterConfig.BigDecimalToDecimal128.INSTANCE
                    .convert(new BigDecimal(raw));
            assertThat(MongoConverterConfig.Decimal128ToBigDecimal.INSTANCE.convert(stored).scale())
                    .as("scale of %s", raw)
                    .isEqualTo(3);
        }
    }

    @Test
    void shouldTolerateNulls() {
        assertThat(MongoConverterConfig.BigDecimalToDecimal128.INSTANCE.convert(null)).isNull();
        assertThat(MongoConverterConfig.Decimal128ToBigDecimal.INSTANCE.convert(null)).isNull();
    }
}