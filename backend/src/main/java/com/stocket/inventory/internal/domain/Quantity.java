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

    public static BigDecimal available(String value) {
        try {
            BigDecimal normalized = InventoryRules.normalizeDecimal(new BigDecimal(value));
            if (normalized.signum() < 0) {
                throw InventoryRules.violation("NEGATIVE_STOCK", "Available quantity cannot be negative");
            }
            return normalized;
        } catch (NumberFormatException exception) {
            throw InventoryRules.violation("INVALID_QUANTITY", "Quantity must be a decimal string");
        }
    }
}
