package com.stocket.reminder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.stocket.reminder.internal.lifecycle.ReminderCalculator;
import com.stocket.reminder.internal.rule.EffectiveReminderRule;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderCalculatorTest {

    private final ReminderCalculator calculator = new ReminderCalculator();
    private final ZoneId zone = ZoneId.of("Asia/Shanghai");

    @Test
    void schedulesAtHouseholdNineAmAndOpensPastTriggerImmediately() {
        EffectiveReminderRule rule = new EffectiveReminderRule(List.of(7, 1, 0), null);
        Instant now = Instant.parse("2026-07-14T02:00:00Z");

        var schedules = calculator.expiration(LocalDate.of(2026, 7, 20), zone, now, rule);

        assertThat(schedules).extracting(schedule -> schedule.triggerKey())
                .containsExactly("EXPIRING:2026-07-20:7", "EXPIRING:2026-07-20:1", "EXPIRING:2026-07-20:0");
        assertThat(schedules.getFirst().status()).isEqualTo("OPEN");
        assertThat(schedules.get(1).triggerAt()).isEqualTo(Instant.parse("2026-07-19T01:00:00Z"));
        assertThat(schedules.get(1).status()).isEqualTo("SCHEDULED");
    }

    @Test
    void expiredEntryCreatesOnlyExpiredReminder() {
        var schedules = calculator.expiration(LocalDate.of(2026, 7, 14), zone,
                Instant.parse("2026-07-14T02:00:00Z"),
                new EffectiveReminderRule(List.of(30, 7, 1, 0), null));

        assertThat(schedules).hasSize(1);
        assertThat(schedules.getFirst().type()).isEqualTo("EXPIRED");
        assertThat(schedules.getFirst().triggerKey()).isEqualTo("EXPIRED:2026-07-14");
        assertThat(schedules.getFirst().status()).isEqualTo("OPEN");
    }

    @Test
    void entryWithoutExpirationCreatesNoExpirationReminder() {
        assertThat(calculator.expiration(null, zone, Instant.now(),
                new EffectiveReminderRule(List.of(30, 7, 1, 0), null))).isEmpty();
    }
}
