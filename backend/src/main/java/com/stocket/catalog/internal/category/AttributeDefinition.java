package com.stocket.catalog.internal.category;

import java.util.List;

public record AttributeDefinition(
        String key,
        String label,
        AttributeType type,
        boolean required,
        Object defaultValue,
        List<String> options,
        int order
) {
    public AttributeDefinition {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
