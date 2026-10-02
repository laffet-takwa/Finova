package com.finova.transaction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Business limits and scheduler cadences for the transaction service.
 * <p>
 * Every value is overridable per environment so a deployment can tighten the
 * transfer ceiling or pause the reconciler without a code change.
 */
@ConfigurationProperties(prefix = "finova.transactions")
public class TransactionProperties {

    /** Largest amount a single transfer may move. */
    private BigDecimal maxAmount = new BigDecimal("1000000.000");

    /** Smallest amount a single transfer may move. */
    private BigDecimal minAmount = new BigDecimal("0.001");

    private int defaultPageSize = 20;

    private int maxPageSize = 100;


    private long reconcileIntervalMs = 15000L;

    private int reconcileThresholdSeconds = 30;

    private long outboxIntervalMs = 500L;

    private int outboxBatchSize = 100;

    private int outboxMaxAttempts = 25;

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public void setMaxAmount(BigDecimal maxAmount) {
        this.maxAmount = maxAmount;
    }

    public BigDecimal getMinAmount() {
        return minAmount;
    }

    public void setMinAmount(BigDecimal minAmount) {
        this.minAmount = minAmount;
    }

    public int getDefaultPageSize() {
        return defaultPageSize;
    }

    public void setDefaultPageSize(int defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }


    public long getReconcileIntervalMs() {
        return reconcileIntervalMs;
    }

    public void setReconcileIntervalMs(long reconcileIntervalMs) {
        this.reconcileIntervalMs = reconcileIntervalMs;
    }

    public int getReconcileThresholdSeconds() {
        return reconcileThresholdSeconds;
    }

    public void setReconcileThresholdSeconds(int reconcileThresholdSeconds) {
        this.reconcileThresholdSeconds = reconcileThresholdSeconds;
    }

    public long getOutboxIntervalMs() {
        return outboxIntervalMs;
    }

    public void setOutboxIntervalMs(long outboxIntervalMs) {
        this.outboxIntervalMs = outboxIntervalMs;
    }

    public int getOutboxBatchSize() {
        return outboxBatchSize;
    }

    public void setOutboxBatchSize(int outboxBatchSize) {
        this.outboxBatchSize = outboxBatchSize;
    }

    public int getOutboxMaxAttempts() {
        return outboxMaxAttempts;
    }

    public void setOutboxMaxAttempts(int outboxMaxAttempts) {
        this.outboxMaxAttempts = outboxMaxAttempts;
    }
}