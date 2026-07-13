package com.stocket.inventory.internal.domain;

public record ShelfLife(int value, ShelfLifeUnit unit) {

    public ShelfLife {
        if (value <= 0 || unit == null) {
            throw InventoryRules.violation("INVALID_SHELF_LIFE", "Shelf life must be positive and have a unit");
        }
    }
}
