package com.stocket.identity.internal.authentication;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window rate limiter with bounded key capacity.
 *
 * @param <K> the type of key used to track rate limits
 */
public class BoundedRateLimiter<K> {

    private final Clock clock;
    private final Duration window;
    private final int maxAttempts;
    private final int maxKeys;
    private final ConcurrentHashMap<K, WindowCounter> counters = new ConcurrentHashMap<>();

    public BoundedRateLimiter(Clock clock, Duration window, int maxAttempts, int maxKeys) {
        this.clock = clock;
        this.window = window;
        this.maxAttempts = maxAttempts;
        this.maxKeys = maxKeys;
    }

    public boolean tryAcquire(K key) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(window);

        // Clean up expired entries on every operation
        counters.entrySet().removeIf(entry -> entry.getValue().isBefore(windowStart));

        // Evict the entry with the earliest window start if at capacity
        if (counters.size() >= maxKeys && !counters.containsKey(key)) {
            counters.entrySet().stream()
                    .min(Comparator.comparing(e -> e.getValue().windowStart()))
                    .ifPresent(oldest -> counters.remove(oldest.getKey()));
        }

        WindowCounter counter = counters.compute(key, (k, existing) -> {
            if (existing == null || existing.isBefore(windowStart)) {
                return new WindowCounter(now, 1);
            }
            return new WindowCounter(existing.windowStart, existing.count + 1);
        });

        return counter.count <= maxAttempts;
    }

    public void reset(K key) {
        counters.remove(key);
    }

    public void clear() {
        counters.clear();
    }

    public int trackedKeyCount() {
        return counters.size();
    }

    private record WindowCounter(Instant windowStart, int count) {
        boolean isBefore(Instant cutoff) {
            return windowStart.isBefore(cutoff);
        }
    }
}
