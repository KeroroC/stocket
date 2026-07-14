package com.stocket.reminder.internal.lifecycle;

import java.time.Instant;

public record ReminderSchedule(String type, String triggerKey, Instant triggerAt, String status) {
}
