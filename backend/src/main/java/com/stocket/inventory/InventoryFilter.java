package com.stocket.inventory;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@org.springframework.modulith.NamedInterface("api")
public record InventoryFilter(UUID itemId, UUID locationId, String type, String assetStatus,
                              LocalDate expiresFrom, LocalDate expiresTo, boolean includeArchived) {
    public String normalizedType() { return normalize(type); }
    public String normalizedAssetStatus() { return normalize(assetStatus); }

    public void validate() {
        String normalizedType = normalizedType();
        String normalizedStatus = normalizedAssetStatus();
        if ((normalizedType != null && !List.of("BATCH", "ASSET").contains(normalizedType))
                || (normalizedStatus != null && !List.of("AVAILABLE", "IN_USE", "LOANED", "LOST", "RETIRED").contains(normalizedStatus))
                || (expiresFrom != null && expiresTo != null && expiresFrom.isAfter(expiresTo))) {
            throw new IllegalArgumentException("INVENTORY_FILTER_INVALID");
        }
    }

    private String normalize(String value) { return value == null || value.isBlank() ? null : value.toUpperCase(Locale.ROOT); }
}
