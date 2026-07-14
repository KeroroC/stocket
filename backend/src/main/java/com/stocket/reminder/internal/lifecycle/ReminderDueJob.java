package com.stocket.reminder.internal.lifecycle;

import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.stocket.notification.NotificationRequested;

@Component
public class ReminderDueJob {

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    ReminderDueJob(JdbcTemplate jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    @Transactional
    public int openDue() {
        List<OpenedReminder> opened = jdbc.query("""
                with due as (
                    select id from reminder
                    where status='SCHEDULED' and trigger_at<=now()
                    order by trigger_at, id
                    for update skip locked
                    limit 200
                )
                update reminder
                set status='OPEN', opened_at=now(), updated_at=now(), version=version+1
                where id in (select id from due)
                returning id,household_id
                """, (result, row) -> new OpenedReminder(
                result.getObject("id", UUID.class), result.getObject("household_id", UUID.class)));
        opened.forEach(reminder -> events.publishEvent(
                new NotificationRequested(reminder.id(), reminder.householdId(), java.time.Instant.now())));
        return opened.size();
    }

    private record OpenedReminder(UUID id, UUID householdId) {
    }
}
