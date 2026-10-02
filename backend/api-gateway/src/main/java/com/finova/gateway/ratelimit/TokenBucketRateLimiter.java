package com.finova.gateway.ratelimit;

import com.finova.gateway.config.RateLimitProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory token bucket, one bucket per client key. No Redis is involved, so
 * the gateway starts and runs with no external dependency; the trade-off is that
 * the effective ceiling is per gateway instance.
 */
@Component
public class TokenBucketRateLimiter {

    private static final double NANOS_PER_MILLI = 1_000_000.0d;
    private static final long RETRY_CEILING_MILLIS = 60_000L;

    private final ConcurrentHashMap<String, AtomicReference<Bucket>> buckets = new ConcurrentHashMap<>();
    private final RateLimitProperties properties;

    public TokenBucketRateLimiter(RateLimitProperties properties) {
        this.properties = properties;
    }

    /**
     * Takes one token from the bucket of {@code key}, refilling it first.
     *
     * @return the outcome, including how long the caller must wait before the
     *         next token becomes available
     */
    public Decision tryAcquire(String key, int capacity, long windowMillis) {
        if (capacity <= 0) {
            return Decision.permit();
        }
        long now = System.nanoTime();
        double refillPerNano = 1.0d / Math.max(1L, windowMillis * 1_000_000L / capacity);
        AtomicReference<Bucket> bucket = buckets.computeIfAbsent(key,
                ignored -> new AtomicReference<>(new Bucket(capacity, now)));
        while (true) {
            Bucket current = bucket.get();
            long elapsed = now - current.lastRefillNanos();
            double tokens = Math.min(capacity, current.tokens() + (elapsed * refillPerNano));
            if (tokens >= 1.0d) {
                if (bucket.compareAndSet(current, new Bucket(tokens - 1.0d, now))) {
                    return Decision.permit();
                }
            } else {
                long waitMillis = (long) Math.ceil((1.0d - tokens) / (refillPerNano * NANOS_PER_MILLI));
                bucket.compareAndSet(current, new Bucket(tokens, now));
                return Decision.throttle(Math.min(RETRY_CEILING_MILLIS, Math.max(1L, waitMillis)));
            }
        }
    }

    /** Drops buckets of clients that have been idle for longer than two windows. */
    @Scheduled(fixedDelayString = "${finova.gateway.rate-limit.cleanup-interval-seconds:60}000")
    public void evictIdleBuckets() {
        evictIdleBuckets(Math.max(1L, properties.getWindowSeconds()) * 2_000L);
    }

    public void evictIdleBuckets(long retentionMillis) {
        long cutoff = System.nanoTime() - retentionMillis * 1_000_000L;
        buckets.entrySet().removeIf(entry -> entry.getValue().get().lastRefillNanos() < cutoff);
    }

    public int bucketCount() {
        return buckets.size();
    }

    public void reset() {
        buckets.clear();
    }

    /** Immutable bucket snapshot; updates are published with compare-and-set. */
    private record Bucket(double tokens, long lastRefillNanos) {
    }

    public record Decision(boolean allowed, long retryAfterMillis) {

        static Decision permit() {
            return new Decision(true, 0L);
        }

        static Decision throttle(long retryAfterMillis) {
            return new Decision(false, retryAfterMillis);
        }

        public long retryAfterSeconds() {
            return Math.max(1L, (retryAfterMillis + 999L) / 1000L);
        }
    }
}
