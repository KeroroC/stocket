package com.stocket.reminder.internal.lifecycle;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@EnableScheduling
public class ReminderDueJob {

    private final JdbcTemplate jdbc;

    ReminderDueJob(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public int openDue() {
        List<UUID> opened = jdbc.queryForList("""
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
                returning id
                """, UUID.class);
        return opened.size();
    }
}
