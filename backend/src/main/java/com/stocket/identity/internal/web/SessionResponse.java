package com.stocket.identity.internal.web;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        boolean current,
        Instant createdAt,
        Instant lastSeenAt,
        Instant absoluteExpiresAt,
        String userAgent,
        String sourceAddress
) {
}
