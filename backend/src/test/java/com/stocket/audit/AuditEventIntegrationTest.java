package com.stocket.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@DisabledInAotMode
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class AuditEventIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");
    @Autowired DataSource dataSource;
    @Autowired ApplicationEventPublisher publisher;

    @Test void persistsHouseholdScopedWhitelistedEventAndDeduplicatesByEventId() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("truncate user_account, household cascade");
        UUID householdId = UUID.randomUUID(), accountId = UUID.randomUUID(), eventId = UUID.randomUUID(), subjectId = UUID.randomUUID();
        jdbc.update("insert into household(id,singleton_key,name,timezone) values (?,1,'家','Asia/Shanghai')", householdId);
        jdbc.update("insert into user_account(id,username,normalized_username,display_name,password_hash,status,must_change_password,credentials_changed_at,created_at,updated_at,version) values (?,'actor','actor','Actor','h','ACTIVE',false,now(),now(),now(),0)", accountId);
        AuditEvent event = new AuditEvent(eventId, householdId, Instant.now(), "AttachmentUploaded", "SUCCESS",
                accountId, "ATTACHMENT", subjectId, "audit-request-123", "api",
                Map.of("ownerType", "ITEM_DEFINITION", "purpose", "ITEM_IMAGE", "ignored", "drop"));

        publisher.publishEvent(event);
        publisher.publishEvent(event);

        Map<String, Object> row = jdbc.queryForMap("select household_id,request_id,details::text details from audit_log where id=?", eventId);
        assertThat(row.get("household_id")).isEqualTo(householdId);
        assertThat(row.get("request_id")).isEqualTo("audit-request-123");
        assertThat(row.get("details").toString()).contains("ownerType").doesNotContain("ignored");
        assertThat(jdbc.queryForObject("select count(*) from audit_log where id=?", Integer.class, eventId)).isEqualTo(1);
    }
}
