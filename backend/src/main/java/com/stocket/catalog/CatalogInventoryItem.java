package com.stocket.catalog;

import java.util.UUID;

public record CatalogInventoryItem(
        UUID id,
        String name,
        boolean archived,
        Integer defaultShelfLifeValue,
        String defaultShelfLifeUnit
) {
}
