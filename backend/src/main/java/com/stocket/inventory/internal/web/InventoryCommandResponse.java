package com.stocket.inventory.internal.web;

import java.time.LocalDate;
import java.util.UUID;

import com.stocket.inventory.internal.domain.AssetStatus;
import com.stocket.inventory.internal.domain.InventoryType;

public record InventoryCommandResponse(
        UUID id,
        InventoryType type,
        String quantity,
        UUID locationId,
        LocalDate expirationDate,
        AssetStatus assetStatus,
        long version,
        String requestId
) {
}
