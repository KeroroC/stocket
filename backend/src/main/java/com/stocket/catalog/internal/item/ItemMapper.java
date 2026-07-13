package com.stocket.catalog.internal.item;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.stocket.catalog.CatalogItemSummary;

final class ItemMapper {
    private ItemMapper() {
    }

    static ItemResponse toResponse(ItemDefinition item) {
        return new ItemResponse(item.id(), item.name(), item.categoryId(), item.brand(), item.model(),
                item.specification(), item.defaultUnit(), item.defaultShelfLifeValue(), item.defaultShelfLifeUnit(),
                immutableValues(item.customAttributes()), item.barcodes().stream().map(ItemBarcode::rawValue).toList(),
                item.tags().stream().map(ItemTag::value).toList(), item.version(), item.archived());
    }

    static CatalogItemSummary toSummary(ItemDefinition item) {
        return new CatalogItemSummary(item.id(), item.name(), item.categoryId(), item.defaultUnit(), item.archived());
    }

    private static Map<String, Object> immutableValues(Map<String, Object> attributes) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
