package com.stocket.reminder;

import java.time.Instant;
import java.util.UUID;

@org.springframework.modulith.NamedInterface("api")
public record ReminderSummary(
        UUID id,
        UUID itemId,
        UUID inventoryEntryId,
        String type,
        String triggerKey,
        Instant triggerAt,
        String status
) {
}
