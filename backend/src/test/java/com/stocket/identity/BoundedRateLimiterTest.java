package com.stocket.identity;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.stocket.identity.internal.authentication.BoundedRateLimiter;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedRateLimiterTest {

    private MutableClock clock = new MutableClock(Instant.parse("2025-01-01T00:00:00Z"));

    @Test
    void allowsUpToMaxAttempts() {
        var limiter = new BoundedRateLimiter<String>(clock, Duration.ofMinutes(15), 10, 1000);

        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire("key")).isTrue();
        }
    }

    @Test
    void rejectsEleventhAttempt() {
        var limiter = new BoundedRateLimiter<String>(clock, Duration.ofMinutes(15), 10, 1000);

        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("key");
        }

        assertThat(limiter.tryAcquire("key")).isFalse();
    }

    @Test
    void recoversAfterWindowPasses() {
        var limiter = new BoundedRateLimiter<String>(clock, Duration.ofMinutes(15), 10, 1000);

        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("key");
        }
        assertThat(limiter.tryAcquire("key")).isFalse();

        // Advance clock past the window
        clock.advance(Duration.ofMinutes(16));
        assertThat(limiter.tryAcquire("key")).isTrue();
    }

    @Test
    void trackedKeyCountNeverExceedsConfiguredCapacity() {
        var limiter = new BoundedRateLimiter<String>(clock, Duration.ofMinutes(15), 10, 3);

        limiter.tryAcquire("a");
        limiter.tryAcquire("b");
        limiter.tryAcquire("c");
        assertThat(limiter.trackedKeyCount()).isEqualTo(3);

        // Adding a 4th key should evict the oldest
        limiter.tryAcquire("d");
        assertThat(limiter.trackedKeyCount()).isLessThanOrEqualTo(3);
    }

    @Test
    void resetRemovesKeyFromTracking() {
        var limiter = new BoundedRateLimiter<String>(clock, Duration.ofMinutes(15), 10, 1000);

        limiter.tryAcquire("key");
        limiter.tryAcquire("key");
        assertThat(limiter.trackedKeyCount()).isEqualTo(1);

        limiter.reset("key");
        assertThat(limiter.trackedKeyCount()).isEqualTo(0);
    }

    @Test
    void differentKeysAreTrackedIndependently() {
        var limiter = new BoundedRateLimiter<String>(clock, Duration.ofMinutes(15), 2, 1000);

        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse(); // exhausted

        assertThat(limiter.tryAcquire("b")).isTrue(); // different key, still has budget
    }

    @Test
    void cleanupRemovesExpiredEntries() {
        var limiter = new BoundedRateLimiter<String>(clock, Duration.ofMinutes(15), 10, 1000);

        limiter.tryAcquire("old-key");
        assertThat(limiter.trackedKeyCount()).isEqualTo(1);

        clock.advance(Duration.ofMinutes(16));

        // Triggering a new acquire should clean up expired entries
        limiter.tryAcquire("new-key");
        assertThat(limiter.trackedKeyCount()).isEqualTo(1);
    }

    // Minimal mutable clock for testing
    private static class MutableClock extends java.time.Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public java.time.Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
