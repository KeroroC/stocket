package com.stocket.inventory.internal.domain;

import java.math.BigDecimal;

public record Quantity(BigDecimal value) {

    public Quantity {
        value = InventoryRules.normalizeDecimal(value);
        if (value.signum() <= 0) {
            throw InventoryRules.violation("INVALID_QUANTITY", "Quantity must be positive");
        }
    }

    public static Quantity of(String value) {
        try {
            return new Quantity(new BigDecimal(value));
        } catch (NumberFormatException exception) {
            throw InventoryRules.violation("INVALID_QUANTITY", "Quantity must be a decimal string");
        }
    }
}
