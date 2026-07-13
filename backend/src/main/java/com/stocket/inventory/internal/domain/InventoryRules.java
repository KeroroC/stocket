package com.stocket.inventory.internal.domain;

import java.math.BigDecimal;

final class InventoryRules {

    static final BigDecimal MAX_QUANTITY = new BigDecimal("999999999999999.9999");

    private InventoryRules() {
    }

    static BigDecimal normalizeDecimal(BigDecimal value) {
        if (value == null) {
            throw violation("INVALID_QUANTITY", "Quantity is required");
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        if (normalized.scale() > 4) {
            throw violation("INVALID_QUANTITY_SCALE", "Quantity supports at most 4 decimal places");
        }
        if (normalized.abs().compareTo(MAX_QUANTITY) > 0) {
            throw violation("INVALID_QUANTITY_RANGE", "Quantity is outside the supported range");
        }
        return normalized;
    }

    static BigDecimal requireAvailableQuantity(InventoryType type, BigDecimal value) {
        BigDecimal normalized = normalizeDecimal(value);
        if (normalized.signum() < 0) {
            throw violation("NEGATIVE_STOCK", "Available quantity cannot be negative");
        }
        if (type == InventoryType.ASSET
                && normalized.compareTo(BigDecimal.ZERO) != 0
                && normalized.compareTo(BigDecimal.ONE) != 0) {
            throw violation("INVALID_ASSET_QUANTITY", "Asset quantity must be 0 or 1");
        }
        return normalized;
    }

    static InventoryRuleViolationException violation(String code, String message) {
        return new InventoryRuleViolationException(code, message);
    }
}
