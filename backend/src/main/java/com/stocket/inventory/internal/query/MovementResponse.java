package com.stocket.inventory.internal.query;

import java.time.Instant;
import java.util.UUID;

public record MovementResponse(
        UUID id,
        String type,
        String quantityDelta,
        UUID relatedEntryId,
        UUID fromLocationId,
        UUID toLocationId,
        String reason,
        UUID actorAccountId,
        String actorDisplayName,
        String requestId,
        Instant occurredAt
) {
}
