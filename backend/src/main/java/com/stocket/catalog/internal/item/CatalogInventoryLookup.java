package com.stocket.catalog.internal.item;

import java.util.Optional;
import java.util.UUID;

import com.stocket.catalog.CatalogInventoryItem;
import com.stocket.catalog.CatalogInventoryQuery;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class CatalogInventoryLookup implements CatalogInventoryQuery {

    private final ItemRepository items;

    CatalogInventoryLookup(ItemRepository items) {
        this.items = items;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogInventoryItem> find(UUID householdId, UUID itemId) {
        return items.findByHouseholdIdAndId(householdId, itemId)
                .map(item -> new CatalogInventoryItem(
                        item.id(), item.name(), item.archived(), item.defaultShelfLifeValue(),
                        item.defaultShelfLifeUnit() == null ? null : item.defaultShelfLifeUnit().name()));
    }
}
