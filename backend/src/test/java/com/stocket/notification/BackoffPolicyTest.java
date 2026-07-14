package com.stocket.notification;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.stocket.notification.internal.delivery.BackoffPolicy;
import com.stocket.notification.internal.worker.SendResult;

import static org.assertj.core.api.Assertions.assertThat;

class BackoffPolicyTest {

    private final BackoffPolicy policy = new BackoffPolicy(
            Duration.ofSeconds(30), Duration.ofHours(24));

    @Test
    void appliesExponentialBackoffDeterministicJitterAndMaximum() {
        UUID deliveryId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        Instant now = Instant.parse("2026-07-14T00:00:00Z");

        Instant first = policy.nextAttempt(deliveryId, 0, now, null);
        Instant second = policy.nextAttempt(deliveryId, 1, now, null);
        assertThat(Duration.between(now, first)).isBetween(Duration.ofSeconds(30), Duration.ofSeconds(36));
        assertThat(Duration.between(now, second)).isBetween(Duration.ofSeconds(60), Duration.ofSeconds(72));
        assertThat(policy.nextAttempt(deliveryId, 20, now, null)).isEqualTo(now.plus(Duration.ofHours(24)));
        assertThat(policy.nextAttempt(deliveryId, 2, now, Duration.ofMinutes(9)))
                .isEqualTo(now.plus(Duration.ofMinutes(9)));
        assertThat(policy.nextAttempt(deliveryId, 1, now, null)).isEqualTo(second);
    }

    @Test
    void classifiesOnlyNetworkTimeoutRateLimitAndServerErrorsAsRetryable() {
        assertThat(SendResult.fromHttp(204, null).outcome()).isEqualTo("DELIVERED");
        assertThat(SendResult.fromHttp(400, null).outcome()).isEqualTo("PERMANENT_FAILURE");
        assertThat(SendResult.fromHttp(408, null).outcome()).isEqualTo("RETRY");
        assertThat(SendResult.fromHttp(429, Duration.ofMinutes(3)).retryAfter())
                .contains(Duration.ofMinutes(3));
        assertThat(SendResult.fromHttp(500, null).outcome()).isEqualTo("RETRY");
    }
}
