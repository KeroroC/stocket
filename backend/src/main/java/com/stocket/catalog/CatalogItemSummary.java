package com.stocket.catalog;

import java.util.UUID;

@org.springframework.modulith.NamedInterface("api")
public record CatalogItemSummary(UUID id, String name, UUID categoryId, String defaultUnit, boolean archived) {
}
