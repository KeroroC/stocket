package com.stocket.inventory.internal.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import com.stocket.inventory.internal.command.ReceiveInventoryCommand;
import com.stocket.inventory.internal.domain.InventoryType;
import com.stocket.inventory.internal.domain.ShelfLifeUnit;

public record ReceiveInventoryRequest(
        UUID itemId,
        InventoryType type,
        String quantity,
        UUID locationId,
        Instant receivedAt,
        LocalDate productionDate,
        LocalDate expirationDate,
        Integer shelfLifeValue,
        ShelfLifeUnit shelfLifeUnit,
        String batchNumber,
        String assetNumber,
        String serialNumber,
        LocalDate purchaseDate,
        LocalDate warrantyExpiresOn,
        Map<String, Object> customAttributes
) {
    ReceiveInventoryCommand toCommand() {
        return new ReceiveInventoryCommand(
                itemId, type, quantity, locationId, receivedAt, productionDate, expirationDate,
                shelfLifeValue, shelfLifeUnit, batchNumber, assetNumber, serialNumber,
                purchaseDate, warrantyExpiresOn, customAttributes);
    }
}
