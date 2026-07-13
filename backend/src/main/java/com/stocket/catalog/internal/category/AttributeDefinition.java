package com.stocket.catalog.internal.category;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public record AttributeDefinition(
        String key,
        String label,
        AttributeType type,
        boolean required,
        JsonNode defaultValue,
        List<String> options,
        int order
) {
    public AttributeDefinition {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
