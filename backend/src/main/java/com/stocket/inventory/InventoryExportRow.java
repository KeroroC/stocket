package com.stocket.inventory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@org.springframework.modulith.NamedInterface("api")
public record InventoryExportRow(UUID id, UUID itemId, String itemName, UUID locationId, String locationName,
                                 String type, String availableQuantity, Instant receivedAt,
                                 LocalDate productionDate, LocalDate expirationDate, String batchNumber,
                                 String assetNumber, String serialNumber, String assetStatus, boolean archived) { }
