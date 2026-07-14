package com.stocket.reminder.internal.lifecycle;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnBooleanProperty(prefix = "stocket.reminder", name = "due-job-enabled")
class ReminderDueScheduler {

    private final ReminderDueJob dueJob;

    ReminderDueScheduler(ReminderDueJob dueJob) {
        this.dueJob = dueJob;
    }

    @Scheduled(cron = "${stocket.reminder.due-job-cron:0 * * * * *}")
    void run() {
        dueJob.openDue();
    }
}
