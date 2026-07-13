package com.stocket.catalog.internal.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.stocket.catalog.CatalogItemSummary;

final class ItemMapper {
    private ItemMapper() {
    }

    static ItemResponse toResponse(ItemDefinition item) {
        return new ItemResponse(item.id(), item.name(), item.categoryId(), item.brand(), item.model(),
                item.specification(), item.defaultUnit(), item.defaultShelfLifeValue(), item.defaultShelfLifeUnit(),
                jsonValues(item.customAttributes()), item.barcodes().stream().map(ItemBarcode::rawValue).toList(),
                item.tags().stream().map(ItemTag::value).toList(), item.version(), item.archived());
    }

    static CatalogItemSummary toSummary(ItemDefinition item) {
        return new CatalogItemSummary(item.id(), item.name(), item.categoryId(), item.defaultUnit(), item.archived());
    }

    private static Map<String, Object> jsonValues(Map<String, JsonNode> attributes) {
        Map<String, Object> result = new LinkedHashMap<>();
        attributes.forEach((key, value) -> result.put(key, jsonValue(value)));
        return Collections.unmodifiableMap(result);
    }

    private static Object jsonValue(JsonNode node) {
        if (node.isTextual()) return node.textValue();
        if (node.isBoolean()) return node.booleanValue();
        if (node.isIntegralNumber()) return node.longValue();
        if (node.isFloatingPointNumber()) return node.decimalValue();
        if (node.isNull()) return null;
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            node.forEach(value -> values.add(jsonValue(value)));
            return Collections.unmodifiableList(values);
        }
        if (node.isObject()) {
            Map<String, Object> values = new LinkedHashMap<>();
            node.properties().forEach(entry -> values.put(entry.getKey(), jsonValue(entry.getValue())));
            return Collections.unmodifiableMap(values);
        }
        return node.toString();
    }
}
