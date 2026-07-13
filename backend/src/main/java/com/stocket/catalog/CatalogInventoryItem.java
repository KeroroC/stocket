package com.stocket.catalog;

import java.util.UUID;

@org.springframework.modulith.NamedInterface("api")
public record CatalogInventoryItem(
        UUID id,
        String name,
        boolean archived,
        Integer defaultShelfLifeValue,
        String defaultShelfLifeUnit
) {
}
