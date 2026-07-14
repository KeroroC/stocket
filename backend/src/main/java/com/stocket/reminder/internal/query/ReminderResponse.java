package com.stocket.reminder.internal.query;

import java.time.Instant;
import java.util.UUID;

public record ReminderResponse(
        UUID id,
        UUID itemId,
        UUID inventoryEntryId,
        String itemName,
        String locationName,
        String availableQuantity,
        String type,
        String triggerKey,
        Instant triggerAt,
        String status,
        long version
) {
}
