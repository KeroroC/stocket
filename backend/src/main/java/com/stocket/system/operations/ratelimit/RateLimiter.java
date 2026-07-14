package com.stocket.system.operations.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class RateLimiter {
    private final Clock clock;
    private final byte[] hmacKey;
    private final int maxKeys;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Autowired
    public RateLimiter(Clock clock, RateLimitProperties properties) {
        this(clock, randomKey(), properties.getMaxKeys());
    }

    RateLimiter(Clock clock, byte[] hmacKey, int maxKeys) {
        this.clock = clock; this.hmacKey = hmacKey.clone(); this.maxKeys = maxKeys;
    }

    public Decision acquire(String rawKey, RateLimitProperties.Limit limit) {
        Instant now = clock.instant();
        String key = digest(rawKey);
        if (!buckets.containsKey(key) && buckets.size() >= maxKeys) evictOldest();
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(limit.capacity(), now));
        return bucket.acquire(now, limit);
    }

    public void clear(String rawKey) { buckets.remove(digest(rawKey)); }

    int trackedKeyCount() { return buckets.size(); }

    private void evictOldest() {
        synchronized (buckets) {
            if (buckets.size() < maxKeys) return;
            buckets.entrySet().stream().min(Map.Entry.comparingByValue((left, right) -> left.lastSeen.compareTo(right.lastSeen)))
                    .ifPresent(entry -> buckets.remove(entry.getKey(), entry.getValue()));
        }
    }

    private String digest(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to protect rate-limit key", error);
        }
    }

    private static byte[] randomKey() {
        byte[] key = new byte[32]; new SecureRandom().nextBytes(key); return key;
    }

    public record Decision(boolean allowed, Duration retryAfter) {
        static Decision allowedDecision() { return new Decision(true, Duration.ZERO); }
        static Decision denied(Duration retryAfter) { return new Decision(false, retryAfter); }
    }

    private static final class Bucket {
        private double tokens;
        private Instant lastRefill;
        private volatile Instant lastSeen;

        private Bucket(int capacity, Instant now) {
            this.tokens = capacity; this.lastRefill = now; this.lastSeen = now;
        }

        private synchronized Decision acquire(Instant now, RateLimitProperties.Limit limit) {
            double elapsed = Math.max(0, Duration.between(lastRefill, now).toNanos() / 1_000_000_000d);
            double rate = limit.capacity() / (double) limit.window().toNanos() * 1_000_000_000d;
            tokens = Math.min(limit.capacity(), tokens + elapsed * rate);
            lastRefill = now; lastSeen = now;
            if (tokens >= 1d) { tokens -= 1d; return Decision.allowedDecision(); }
            long seconds = Math.max(1, (long) Math.ceil((1d - tokens) / rate));
            return Decision.denied(Duration.ofSeconds(seconds));
        }
    }
}
