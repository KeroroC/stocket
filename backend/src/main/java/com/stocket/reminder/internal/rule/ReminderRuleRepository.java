package com.stocket.reminder.internal.rule;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderRuleRepository extends JpaRepository<ReminderRule, UUID> {

    List<ReminderRule> findByHouseholdIdAndEnabledTrue(UUID householdId);
}
