package com.stocket.catalog;

import java.util.UUID;

public record CatalogItemChanged(UUID householdId, UUID itemId) {
}
