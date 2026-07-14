package com.stocket.notification.internal.delivery;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BackoffPolicy {

    private final Duration initialBackoff;
    private final Duration maximumBackoff;

    public BackoffPolicy(
            @Value("${stocket.notification.initial-backoff:30s}") Duration initialBackoff,
            @Value("${stocket.notification.max-backoff:24h}") Duration maximumBackoff) {
        this.initialBackoff = initialBackoff;
        this.maximumBackoff = maximumBackoff;
    }

    public Instant nextAttempt(UUID deliveryId, int attempt, Instant now, Duration retryAfter) {
        if (retryAfter != null) {
            return now.plus(retryAfter.compareTo(maximumBackoff) > 0 ? maximumBackoff : retryAfter);
        }
        int exponent = Math.min(Math.max(attempt, 0), 30);
        long baseMillis;
        try {
            baseMillis = Math.multiplyExact(initialBackoff.toMillis(), 1L << exponent);
        } catch (ArithmeticException exception) {
            baseMillis = maximumBackoff.toMillis();
        }
        long capped = Math.min(baseMillis, maximumBackoff.toMillis());
        if (capped == maximumBackoff.toMillis()) {
            return now.plus(maximumBackoff);
        }
        long hash = Integer.toUnsignedLong(java.util.Objects.hash(deliveryId, attempt));
        long jitter = Math.round(capped * ((hash % 2001) / 10000.0));
        return now.plusMillis(Math.min(maximumBackoff.toMillis(), capped + jitter));
    }
}
