package com.stocket.inventory.internal.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReconciliationIssue(
        UUID id,
        UUID householdId,
        UUID entryId,
        BigDecimal expectedQuantity,
        BigDecimal actualQuantity,
        String status,
        Instant detectedAt,
        Instant resolvedAt
) {
}
