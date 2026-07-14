package com.stocket.reminder.internal.rule;

import java.util.List;
import java.util.UUID;

public final class ReminderRuleService {

    private static final EffectiveReminderRule DEFAULT_RULE =
            new EffectiveReminderRule(List.of(30, 7, 1, 0), null);

    private ReminderRuleService() {
    }

    public static EffectiveReminderRule select(List<ReminderRule> rules, UUID categoryId, UUID itemId) {
        if (rules == null || rules.isEmpty()) {
            return DEFAULT_RULE;
        }

        return find(rules, "ITEM", itemId)
                .or(() -> find(rules, "CATEGORY", categoryId))
                .or(() -> find(rules, "HOUSEHOLD", null))
                .map(ReminderRule::effectiveRule)
                .orElse(DEFAULT_RULE);
    }

    private static java.util.Optional<ReminderRule> find(List<ReminderRule> rules, String scopeType,
                                                          UUID scopeId) {
        return rules.stream()
                .filter(rule -> scopeType.equals(rule.scopeType()))
                .filter(rule -> java.util.Objects.equals(scopeId, rule.scopeId()))
                .findFirst();
    }
}
