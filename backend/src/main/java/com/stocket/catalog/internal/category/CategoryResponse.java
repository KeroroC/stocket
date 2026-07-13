package com.stocket.catalog.internal.category;

import java.util.List;
import java.util.UUID;

record CategoryResponse(
        UUID id,
        UUID parentId,
        String name,
        InventoryType defaultInventoryType,
        List<AttributeDefinition> attributeSchema,
        long version,
        boolean archived
) {
}
