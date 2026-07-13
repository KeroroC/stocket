package com.stocket.inventory.internal.query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record InventoryEntryResponse(
        UUID id,
        UUID itemId,
        String itemName,
        UUID locationId,
        String locationName,
        String type,
        String quantity,
        Instant receivedAt,
        LocalDate productionDate,
        LocalDate expirationDate,
        Map<String, Object> customAttributes,
        long version,
        boolean archived,
        String batchNumber,
        UUID sourceEntryId,
        Integer shelfLifeValue,
        String shelfLifeUnit,
        String assetNumber,
        String serialNumber,
        LocalDate purchaseDate,
        LocalDate warrantyExpiresOn,
        String assetStatus
) {
}
