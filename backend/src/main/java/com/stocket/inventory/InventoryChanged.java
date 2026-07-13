package com.stocket.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryChanged(
        UUID eventId,
        UUID householdId,
        UUID itemId,
        UUID entryId,
        String operation,
        BigDecimal quantityDelta,
        Instant occurredAt
) {
}
