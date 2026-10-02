package com.finova.fraud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every fraud threshold and weight, bound from {@code finova.fraud.*}.
 * <p>
 * The rule implementations receive the nested settings objects directly, which keeps them free of
 * any Spring type and therefore trivially unit testable with {@code new LargeAmountRule(props)}.
 */
@ConfigurationProperties(prefix = "finova.fraud")
public class FraudProperties {

    /**
     * Added once per rule beyond the first that fires. Two independent signals agreeing is
     * stronger evidence than their arithmetic sum, and the bonus is what lifts the canonical
     * "large amount plus unusual pattern" case from 80 to 88.
     */
    private int corroborationBonus = 8;

    private final LargeAmount largeAmount = new LargeAmount();
    private final Velocity velocity = new Velocity();
    private final UnusualPattern unusualPattern = new UnusualPattern();
    private final NewRecipient newRecipient = new NewRecipient();
    private final RoundAmount roundAmount = new RoundAmount();
    private final VelocityState velocityState = new VelocityState();
    private final Outbox outbox = new Outbox();

    public int getCorroborationBonus() {
        return corroborationBonus;
    }

    public void setCorroborationBonus(int corroborationBonus) {
        this.corroborationBonus = corroborationBonus;
    }

    public LargeAmount getLargeAmount() {
        return largeAmount;
    }

    public Velocity getVelocity() {
        return velocity;
    }

    public UnusualPattern getUnusualPattern() {
        return unusualPattern;
    }

    public NewRecipient getNewRecipient() {
        return newRecipient;
    }

    public RoundAmount getRoundAmount() {
        return roundAmount;
    }

    public VelocityState getVelocityState() {
        return velocityState;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    /** Review threshold per settlement currency. Amounts strictly above the threshold are flagged. */
    public static class LargeAmount {
        private BigDecimal tnd = new BigDecimal("10000");
        private BigDecimal eur = new BigDecimal("2500");
        private BigDecimal usd = new BigDecimal("3000");
        private int score = 55;

        public BigDecimal getTnd() {
            return tnd;
        }

        public void setTnd(BigDecimal tnd) {
            this.tnd = tnd;
        }

        public BigDecimal getEur() {
            return eur;
        }

        public void setEur(BigDecimal eur) {
            this.eur = eur;
        }

        public BigDecimal getUsd() {
            return usd;
        }

        public void setUsd(BigDecimal usd) {
            this.usd = usd;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }

        /** Threshold for a currency, falling back to the TND threshold for anything unknown. */
        public BigDecimal thresholdFor(String currency) {
            BigDecimal found = currency == null ? null : thresholds().get(currency.toUpperCase());
            return found == null ? tnd : found;
        }

        public Map<String, BigDecimal> thresholds() {
            Map<String, BigDecimal> all = new LinkedHashMap<>();
            all.put("TND", tnd);
            all.put("EUR", eur);
            all.put("USD", usd);
            return all;
        }
    }

    /** Sliding window used by the burst velocity rule. */
    public static class Velocity {
        private int maxTransactions = 5;
        private long windowSeconds = 60;
        private int mediumScore = 30;
        private int highScore = 45;
        private int highScoreEventCount = 10;

        public int getMaxTransactions() {
            return maxTransactions;
        }

        public void setMaxTransactions(int maxTransactions) {
            this.maxTransactions = maxTransactions;
        }

        public long getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(long windowSeconds) {
            this.windowSeconds = windowSeconds;
        }

        public int getMediumScore() {
            return mediumScore;
        }

        public void setMediumScore(int mediumScore) {
            this.mediumScore = mediumScore;
        }

        public int getHighScore() {
            return highScore;
        }

        public void setHighScore(int highScore) {
            this.highScore = highScore;
        }

        public int getHighScoreEventCount() {
            return highScoreEventCount;
        }

        public void setHighScoreEventCount(int highScoreEventCount) {
            this.highScoreEventCount = highScoreEventCount;
        }
    }

    /** Deviation-from-behaviour multipliers and the local time band treated as unusual. */
    public static class UnusualPattern {
        private BigDecimal averageMultiplier = new BigDecimal("3");
        private BigDecimal largestMultiplier = new BigDecimal("2");
        private String zoneId = "Africa/Tunis";
        private int unusualHourStart = 0;
        private int unusualHourEnd = 5;
        private int score = 25;

        public BigDecimal getAverageMultiplier() {
            return averageMultiplier;
        }

        public void setAverageMultiplier(BigDecimal averageMultiplier) {
            this.averageMultiplier = averageMultiplier;
        }

        public BigDecimal getLargestMultiplier() {
            return largestMultiplier;
        }

        public void setLargestMultiplier(BigDecimal largestMultiplier) {
            this.largestMultiplier = largestMultiplier;
        }

        public String getZoneId() {
            return zoneId;
        }

        public void setZoneId(String zoneId) {
            this.zoneId = zoneId;
        }

        public int getUnusualHourStart() {
            return unusualHourStart;
        }

        public void setUnusualHourStart(int unusualHourStart) {
            this.unusualHourStart = unusualHourStart;
        }

        public int getUnusualHourEnd() {
            return unusualHourEnd;
        }

        public void setUnusualHourEnd(int unusualHourEnd) {
            this.unusualHourEnd = unusualHourEnd;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }
    }

    /** Beneficiary never paid by this sender inside the lookback window. */
    public static class NewRecipient {
        private int lookbackDays = 7;
        private int score = 10;

        public int getLookbackDays() {
            return lookbackDays;
        }

        public void setLookbackDays(int lookbackDays) {
            this.lookbackDays = lookbackDays;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }
    }

    /** Structuring probe: a suspiciously round amount that stays under the review threshold. */
    public static class RoundAmount {
        private BigDecimal minimum = new BigDecimal("1000");
        private BigDecimal multiple = new BigDecimal("1000");
        private int score = 10;

        public BigDecimal getMinimum() {
            return minimum;
        }

        public void setMinimum(BigDecimal minimum) {
            this.minimum = minimum;
        }

        public BigDecimal getMultiple() {
            return multiple;
        }

        public void setMultiple(BigDecimal multiple) {
            this.multiple = multiple;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }
    }

    /** Retention of the per-account rolling baseline. */
    public static class VelocityState {
        private int historySize = 20;
        private int retentionDays = 30;
        private String cleanupCron = "0 17 * * * *";

        public int getHistorySize() {
            return historySize;
        }

        public void setHistorySize(int historySize) {
            this.historySize = historySize;
        }

        public int getRetentionDays() {
            return retentionDays;
        }

        public void setRetentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
        }

        public String getCleanupCron() {
            return cleanupCron;
        }

        public void setCleanupCron(String cleanupCron) {
            this.cleanupCron = cleanupCron;
        }
    }

    /** Transactional-outbox drain settings. */
    public static class Outbox {
        private long publishIntervalMs = 2000;
        private int batchSize = 50;
        private long sendTimeoutMs = 5000;
        private int retentionDays = 7;

        public long getPublishIntervalMs() {
            return publishIntervalMs;
        }

        public void setPublishIntervalMs(long publishIntervalMs) {
            this.publishIntervalMs = publishIntervalMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public long getSendTimeoutMs() {
            return sendTimeoutMs;
        }

        public void setSendTimeoutMs(long sendTimeoutMs) {
            this.sendTimeoutMs = sendTimeoutMs;
        }

        public int getRetentionDays() {
            return retentionDays;
        }

        public void setRetentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
        }
    }
}
