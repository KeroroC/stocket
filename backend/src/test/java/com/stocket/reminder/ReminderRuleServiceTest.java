package com.stocket.reminder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.stocket.reminder.internal.rule.EffectiveReminderRule;
import com.stocket.reminder.internal.rule.ReminderRule;
import com.stocket.reminder.internal.rule.ReminderRuleService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReminderRuleServiceTest {

    private final UUID householdId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();

    @Test
    void itemOverridesCategoryAndCategoryOverridesHousehold() {
        ReminderRule household = ReminderRule.create(householdId, "HOUSEHOLD", null,
                List.of(30, 7), new BigDecimal("5"));
        ReminderRule category = ReminderRule.create(householdId, "CATEGORY", categoryId,
                List.of(14, 3), new BigDecimal("3"));
        ReminderRule item = ReminderRule.create(householdId, "ITEM", itemId,
                List.of(2, 0), new BigDecimal("1"));

        assertThat(ReminderRuleService.select(List.of(household, category, item), categoryId, itemId)
                .expirationOffsets()).containsExactly(2, 0);
        assertThat(ReminderRuleService.select(List.of(household, category), categoryId, itemId)
                .expirationOffsets()).containsExactly(14, 3);
        assertThat(ReminderRuleService.select(List.of(household), categoryId, itemId)
                .expirationOffsets()).containsExactly(30, 7);
    }

    @Test
    void usesDefaultsAndTreatsZeroLowStockThresholdAsDisabled() {
        EffectiveReminderRule defaults = ReminderRuleService.select(List.of(), categoryId, itemId);
        assertThat(defaults.expirationOffsets()).containsExactly(30, 7, 1, 0);
        assertThat(defaults.lowStockThreshold()).isEmpty();

        ReminderRule disabledThreshold = ReminderRule.create(householdId, "ITEM", itemId,
                List.of(7, 1), BigDecimal.ZERO);
        assertThat(ReminderRuleService.select(List.of(disabledThreshold), categoryId, itemId)
                .lowStockThreshold()).isEmpty();
    }

    @Test
    void rejectsDuplicateNegativeOrUnsortedOffsets() {
        assertThatThrownBy(() -> new EffectiveReminderRule(List.of(7, 7, 0), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EffectiveReminderRule(List.of(7, -1), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EffectiveReminderRule(List.of(1, 7), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
