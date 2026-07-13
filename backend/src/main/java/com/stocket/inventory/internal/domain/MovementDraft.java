package com.stocket.inventory.internal.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record MovementDraft(
        MovementType type,
        BigDecimal quantityDelta,
        BigDecimal beforeQuantity,
        BigDecimal afterQuantity,
        UUID fromLocationId,
        UUID toLocationId,
        String reason
) {
}
