package com.personalfinance.gateway;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token buckets, one per key.
 *
 * {@code docker-compose.yml} provisions no Redis, and Spring Cloud Gateway's
 * built-in {@code RequestRateLimiter} is Redis-only, so this is a deliberately
 * smaller substitute rather than new infrastructure for a single-owner app.
 * Buckets reset on restart and are per-process; both are accepted at this scale.
 *
 * Shared by the two limiter filters so the arithmetic exists once — they differ
 * only in what they key on, not in how a bucket behaves.
 */
final class TokenBucketRegistry {

    /**
     * Keys can be unbounded — a client address is chosen by the caller, not by us —
     * so the map is swept once it grows past this. Only idle buckets are dropped,
     * meaning ones refilled back to capacity, so a sweep can never forgive a caller
     * who is currently being limited.
     */
    private static final int SWEEP_THRESHOLD = 10_000;

    private final double capacity;
    private final double refillPerSecond;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    TokenBucketRegistry(double capacity, double refillPerSecond) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
    }

    boolean tryConsume(String key) {
        if (buckets.size() > SWEEP_THRESHOLD) {
            buckets.values().removeIf(Bucket::isFull);
        }
        return buckets.computeIfAbsent(key, ignored -> new Bucket(capacity, refillPerSecond))
                .tryConsume();
    }

    private static final class Bucket {
        private final double capacity;
        private final double refillPerSecond;
        private double tokens;
        private long lastRefillNanos = System.nanoTime();

        Bucket(double capacity, double refillPerSecond) {
            this.capacity = capacity;
            this.refillPerSecond = refillPerSecond;
            this.tokens = capacity;
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }

        /** Full means nobody has spent from this bucket recently — safe to forget. */
        synchronized boolean isFull() {
            refill();
            return tokens >= capacity;
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
            lastRefillNanos = now;
        }
    }
}
