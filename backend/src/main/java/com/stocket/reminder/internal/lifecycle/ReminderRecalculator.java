package com.stocket.reminder.internal.lifecycle;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stocket.reminder.internal.rule.EffectiveReminderRule;
import com.stocket.reminder.internal.rule.ReminderRuleRepository;
import com.stocket.reminder.internal.rule.ReminderRuleService;
import com.stocket.notification.NotificationRequested;

@Service
public class ReminderRecalculator {

    private final JdbcTemplate jdbc;
    private final ReminderRuleRepository ruleRepository;
    private final ReminderRepository reminderRepository;
    private final ReminderCalculator calculator;
    private final ApplicationEventPublisher events;

    ReminderRecalculator(JdbcTemplate jdbc, ReminderRuleRepository ruleRepository,
                         ReminderRepository reminderRepository, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.ruleRepository = ruleRepository;
        this.reminderRepository = reminderRepository;
        this.calculator = new ReminderCalculator();
        this.events = events;
    }

    @Transactional
    public void recalculate(UUID householdId, UUID itemId, UUID entryId) {
        ItemContext item = loadItem(householdId, itemId);
        EntrySnapshot entry = loadEntry(householdId, itemId, entryId);
        EffectiveReminderRule rule = ReminderRuleService.select(
                ruleRepository.findByHouseholdIdAndEnabledTrue(householdId), item.categoryId(), itemId);
        Instant now = Instant.now();

        List<DesiredReminder> desired = new ArrayList<>();
        if (!entry.archived() && entry.availableQuantity().signum() > 0) {
            calculator.expiration(entry.expirationDate(), ZoneId.of(item.timezone()), now, rule).stream()
                    .map(schedule -> new DesiredReminder(
                            schedule.type(), schedule.triggerKey(), schedule.triggerAt(), schedule.status(), entryId))
                    .forEach(desired::add);
        }

        BigDecimal totalAvailable = totalAvailable(householdId, itemId);
        rule.lowStockThreshold()
                .filter(threshold -> totalAvailable.compareTo(threshold) <= 0)
                .map(threshold -> new DesiredReminder(
                        "LOW_STOCK", "LOW_STOCK:" + normalized(threshold), now, "OPEN", null))
                .ifPresent(desired::add);

        List<Reminder> active = reminderRepository.findActiveForRecalculation(householdId, itemId, entryId);
        active.stream()
                .filter(existing -> desired.stream().noneMatch(candidate -> candidate.matches(existing)))
                .forEach(existing -> existing.resolve(now));
        reminderRepository.flush();

        for (DesiredReminder candidate : desired) {
            if (active.stream().noneMatch(candidate::matches)) {
                insert(householdId, itemId, candidate, now);
            }
        }
    }

    private ItemContext loadItem(UUID householdId, UUID itemId) {
        List<ItemContext> items = jdbc.query("""
                select item.category_id, household.timezone
                from item_definition item
                join household on household.id = item.household_id
                where item.household_id=? and item.id=? and item.archived_at is null
                """, (result, row) -> new ItemContext(
                result.getObject("category_id", UUID.class), result.getString("timezone")),
                householdId, itemId);
        if (items.size() != 1) {
            throw new IllegalArgumentException("Inventory item does not belong to household");
        }
        return items.getFirst();
    }

    private EntrySnapshot loadEntry(UUID householdId, UUID itemId, UUID entryId) {
        List<EntrySnapshot> entries = jdbc.query("""
                select expiration_date, available_quantity, archived_at
                from inventory_entry
                where household_id=? and item_definition_id=? and id=?
                """, (result, row) -> new EntrySnapshot(
                result.getObject("expiration_date", LocalDate.class),
                result.getBigDecimal("available_quantity"),
                result.getTimestamp("archived_at") != null),
                householdId, itemId, entryId);
        if (entries.size() != 1) {
            throw new IllegalArgumentException("Inventory entry does not belong to item");
        }
        return entries.getFirst();
    }

    private BigDecimal totalAvailable(UUID householdId, UUID itemId) {
        return jdbc.queryForObject("""
                select coalesce(sum(available_quantity), 0)
                from inventory_entry
                where household_id=? and item_definition_id=? and archived_at is null
                """, BigDecimal.class, householdId, itemId);
    }

    private void insert(UUID householdId, UUID itemId, DesiredReminder reminder, Instant now) {
        UUID reminderId = UUID.randomUUID();
        int inserted = jdbc.update("""
                insert into reminder(id, household_id, item_definition_id, inventory_entry_id,
                    reminder_type, trigger_key, trigger_at, status, opened_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict do nothing
                """, reminderId, householdId, itemId, reminder.entryId(), reminder.type(),
                reminder.triggerKey(), Timestamp.from(reminder.triggerAt()), reminder.status(),
                "OPEN".equals(reminder.status()) ? Timestamp.from(now) : null,
                Timestamp.from(now), Timestamp.from(now));
        if (inserted == 1 && "OPEN".equals(reminder.status())) {
            events.publishEvent(new NotificationRequested(reminderId, householdId, now));
        }
    }

    private String normalized(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private record ItemContext(UUID categoryId, String timezone) {
    }

    private record EntrySnapshot(LocalDate expirationDate, BigDecimal availableQuantity, boolean archived) {
    }

    private record DesiredReminder(String type, String triggerKey, Instant triggerAt,
                                   String status, UUID entryId) {
        boolean matches(Reminder reminder) {
            return reminder.matches(type, triggerKey, entryId);
        }
    }
}
