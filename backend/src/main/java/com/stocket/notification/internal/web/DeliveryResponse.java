package com.stocket.notification.internal.web;

import java.time.Instant;
import java.util.UUID;

public record DeliveryResponse(
        UUID id,
        UUID reminderId,
        UUID memberId,
        String channelType,
        String status,
        int attemptCount,
        Instant nextAttemptAt,
        String lastErrorCode,
        Instant lastErrorAt,
        Instant deliveredAt,
        Instant updatedAt
) {
}
