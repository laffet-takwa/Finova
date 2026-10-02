package com.finova.fraud.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Rolling state for one sender account and currency bucket.
 * <p>
 * Two different lifetimes live in this document and they are deliberately not reset together:
 * <ul>
 *   <li>{@code windowStart} / {@code eventCount} / {@code totalAmount} form the <em>velocity
 *       window</em>. They roll over as soon as {@code windowStart} falls outside the configured
 *       window so the burst rule always counts the last N seconds, not the last N transfers.</li>
 *   <li>{@code recentAmounts} is the <em>behavioural baseline</em>. It is never reset by window
 *       expiry, otherwise a long-idle account would always look like a brand new account and the
 *       unusual-pattern rule would lose its baseline.</li>
 * </ul>
 * {@code transfersSeen} is a lifetime counter and is what makes "first ever transfer" decidable
 * across window resets.
 */
@Document(collection = "velocity_windows")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VelocityWindow {

    /** {@code senderAccountId + ":" + currency}. */
    @Id
    private String id;

    @Indexed
    private String senderAccountId;

    private String senderUserId;
    private String senderAccountNumber;
    private String currency;
    private Instant windowStart;
    private int eventCount;
    private BigDecimal totalAmount;

    /** Newest first, capped at {@code finova.fraud.velocity-state.history-size}. */
    private List<BigDecimal> recentAmounts;

    private long transfersSeen;
    private Instant firstSeenAt;
    @Indexed
    private Instant updatedAt;

    public static String idFor(String senderAccountId, String currency) {
        return senderAccountId + ":" + currency;
    }

    public List<BigDecimal> amountsOrEmpty() {
        return recentAmounts == null ? List.of() : recentAmounts;
    }

    /** Arithmetic mean of the retained baseline, or zero when nothing has been observed yet. */
    public BigDecimal averageAmount() {
        List<BigDecimal> amounts = amountsOrEmpty();
        if (amounts.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(amounts.size()), com.finova.common.support.Money.SCALE,
                java.math.RoundingMode.HALF_EVEN);
    }

    /** Largest retained amount, or zero when nothing has been observed yet. */
    public BigDecimal largestAmount() {
        return amountsOrEmpty().stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    public boolean isFirstEverTransfer() {
        return transfersSeen <= 1;
    }

    public void remember(BigDecimal amount, int historySize, Instant now) {
        if (recentAmounts == null) {
            recentAmounts = new ArrayList<>();
        }
        recentAmounts.add(0, amount);
        while (recentAmounts.size() > historySize) {
            recentAmounts.remove(recentAmounts.size() - 1);
        }
        updatedAt = now;
    }
}
