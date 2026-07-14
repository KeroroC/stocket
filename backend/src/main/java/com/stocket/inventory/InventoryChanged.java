package com.stocket.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@org.springframework.modulith.NamedInterface("api")
public record InventoryChanged(
        UUID eventId,
        UUID householdId,
        UUID itemId,
        UUID entryId,
        String operation,
        BigDecimal quantityDelta,
        Instant occurredAt,
        String requestId
) {
}
