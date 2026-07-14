package com.stocket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SchedulingRegistrationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired(required = false)
    ScheduledAnnotationBeanPostProcessor scheduledTasks;

    @Test
    void doesNotRegisterBackgroundJobsUnlessExplicitlyEnabled() {
        if (scheduledTasks != null) {
            assertThat(scheduledTasks.getScheduledTasks())
                    .noneMatch(task -> task.toString().contains("DeliveryWorker")
                            || task.toString().contains("ReminderDueJob"));
        }
    }
}
