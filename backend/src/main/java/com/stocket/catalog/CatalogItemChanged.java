package com.stocket.catalog;

import java.util.UUID;

@org.springframework.modulith.NamedInterface("api")
public record CatalogItemChanged(UUID householdId, UUID itemId) {
}
