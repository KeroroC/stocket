package com.stocket.reminder.internal.web;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotNull;

public record ReminderRuleRequest(
        @NotNull List<Integer> expirationOffsets,
        BigDecimal lowStockThreshold,
        boolean enabled,
        long version
) {
}
