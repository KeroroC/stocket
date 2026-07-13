package com.stocket.catalog;

import java.util.UUID;

public record CatalogItemSummary(UUID id, String name, UUID categoryId, String defaultUnit, boolean archived) {
}
