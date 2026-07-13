package com.stocket.catalog.internal.category;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record CategoryRequest(
        @NotBlank @Size(max = 120) String name,
        UUID parentId,
        @NotNull InventoryType defaultInventoryType,
        List<AttributeDefinition> attributeSchema,
        Long version
) {
    CategoryRequest {
        attributeSchema = attributeSchema == null ? List.of() : List.copyOf(attributeSchema);
    }
}
