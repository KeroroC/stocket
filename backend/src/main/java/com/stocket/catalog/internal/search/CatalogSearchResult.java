package com.stocket.catalog.internal.search;

import java.util.List;
import java.util.UUID;

record CatalogSearchResult(List<SearchItem> items, int page, int size, long total) {
    record SearchItem(UUID id, String name, String categoryPath, String brand, String model,
                      String specification, List<String> tags, List<String> barcodes, String matchType) { }
}
