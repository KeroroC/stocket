package com.stocket.catalog;

import java.util.Optional;
import java.util.UUID;

public interface CatalogInventoryQuery {

    Optional<CatalogInventoryItem> find(UUID householdId, UUID itemId);
}
