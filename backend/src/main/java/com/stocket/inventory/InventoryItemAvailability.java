package com.stocket.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryItemAvailability(
        UUID itemId,
        BigDecimal totalAvailable,
        LocalDate earliestExpiration,
        int activeEntryCount
) {
}
