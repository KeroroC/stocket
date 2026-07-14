package com.stocket.reminder.internal.lifecycle;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import com.stocket.reminder.internal.rule.EffectiveReminderRule;

public final class ReminderCalculator {

    private static final LocalTime TRIGGER_TIME = LocalTime.of(9, 0);

    public List<ReminderSchedule> expiration(LocalDate expirationDate, ZoneId householdZone,
                                             Instant now, EffectiveReminderRule rule) {
        if (expirationDate == null) {
            return List.of();
        }

        LocalDate today = now.atZone(householdZone).toLocalDate();
        if (!expirationDate.isAfter(today)) {
            Instant triggerAt = expirationDate.atTime(TRIGGER_TIME).atZone(householdZone).toInstant();
            return List.of(new ReminderSchedule(
                    "EXPIRED",
                    "EXPIRED:" + expirationDate,
                    triggerAt,
                    "OPEN"));
        }

        return rule.expirationOffsets().stream()
                .map(offset -> scheduleExpiring(expirationDate, householdZone, now, offset))
                .toList();
    }

    private ReminderSchedule scheduleExpiring(LocalDate expirationDate, ZoneId householdZone,
                                               Instant now, int offset) {
        Instant triggerAt = expirationDate.minusDays(offset)
                .atTime(TRIGGER_TIME)
                .atZone(householdZone)
                .toInstant();
        String status = triggerAt.isAfter(now) ? "SCHEDULED" : "OPEN";
        return new ReminderSchedule(
                "EXPIRING",
                "EXPIRING:" + expirationDate + ":" + offset,
                triggerAt,
                status);
    }
}
