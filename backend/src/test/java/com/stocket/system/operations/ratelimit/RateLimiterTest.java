package com.stocket.system.operations.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {
    @Test void refillsAfterWindowAndNeverExceedsCapacityUnderConcurrency() throws Exception {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = new RateLimiter(clock, "01234567890123456789012345678901".getBytes(), 1_000);
        RateLimitProperties.Limit limit = new RateLimitProperties.Limit(10, Duration.ofMinutes(1));
        AtomicInteger allowed = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(20)) {
            for (int index = 0; index < 100; index++) {
                executor.submit(() -> { if (limiter.acquire("same-account-and-ip", limit).allowed()) allowed.incrementAndGet(); });
            }
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(allowed).hasValue(10);
        assertThat(limiter.acquire("same-account-and-ip", limit).retryAfter()).isPositive();

        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.acquire("same-account-and-ip", limit).allowed()).isTrue();
        assertThat(limiter.trackedKeyCount()).isEqualTo(1);
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-07-14T00:00:00Z");
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
