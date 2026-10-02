package com.finova.common.support;

import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money is always handled as {@link BigDecimal} scaled to the currency minor
 * unit. Rounding is explicit and uniform so a balance can never drift by a
 * fraction of a millim.
 */
public final class Money {

    public static final int SCALE = 3;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_EVEN);

    private Money() {
    }

    public static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(SCALE, RoundingMode.HALF_EVEN);
    }

    public static BigDecimal normalize(BigDecimal value) {
        return scale(value);
    }

    public static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Amount must be greater than zero.");
        }
    }
}
