package com.stocket.reminder.internal.rule;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

public final class EffectiveReminderRule {

    private final List<Integer> expirationOffsets;
    private final BigDecimal lowStockThreshold;

    public EffectiveReminderRule(List<Integer> expirationOffsets, BigDecimal lowStockThreshold) {
        if (expirationOffsets == null) {
            throw new IllegalArgumentException("Expiration offsets are required");
        }

        List<Integer> offsets = List.copyOf(expirationOffsets);
        if (offsets.stream().anyMatch(offset -> offset == null || offset < 0)) {
            throw new IllegalArgumentException("Expiration offsets must be non-negative");
        }
        if (new HashSet<>(offsets).size() != offsets.size()) {
            throw new IllegalArgumentException("Expiration offsets must be unique");
        }
        for (int index = 1; index < offsets.size(); index++) {
            if (offsets.get(index - 1) < offsets.get(index)) {
                throw new IllegalArgumentException("Expiration offsets must be in descending order");
            }
        }
        if (lowStockThreshold != null && lowStockThreshold.signum() < 0) {
            throw new IllegalArgumentException("Low-stock threshold must be non-negative");
        }

        this.expirationOffsets = offsets;
        this.lowStockThreshold = lowStockThreshold == null || lowStockThreshold.signum() == 0
                ? null
                : lowStockThreshold;
    }

    public List<Integer> expirationOffsets() {
        return expirationOffsets;
    }

    public Optional<BigDecimal> lowStockThreshold() {
        return Optional.ofNullable(lowStockThreshold);
    }
}
