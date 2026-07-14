package com.stocket.attachment;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
@DisabledInAotMode
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class AttachmentSchemaIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired DataSource dataSource;
    private final UUID ownerId = UUID.randomUUID();

    @Test
    void createsAttachmentConstraintsAndAuditIndexes() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID household = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        jdbc.update("insert into household(id,singleton_key,name,timezone) values (?,1,'家','Asia/Shanghai')", household);
        jdbc.update("""
                insert into user_account(id,username,normalized_username,display_name,password_hash,status,
                    must_change_password,credentials_changed_at,created_at,updated_at,version)
                values (?,'owner','owner','Owner','hash','ACTIVE',false,now(),now(),now(),0)
                """, account);

        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_name='attachment'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("select indexname from pg_indexes where tablename='audit_log'", String.class))
                .contains("idx_audit_household_time", "idx_audit_household_actor", "idx_audit_household_event", "idx_audit_household_request");

        insert(jdbc, household, account, "cover-one", "ITEM_DEFINITION", "COVER_IMAGE", "AVAILABLE");
        assertThatThrownBy(() -> insert(jdbc, household, account, "cover-one", "ITEM_DEFINITION", "ITEM_IMAGE", "AVAILABLE"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insert(jdbc, household, account, "cover-two", "ITEM_DEFINITION", "COVER_IMAGE", "AVAILABLE"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insert(jdbc, household, account, "bad-owner", "PATH", "ITEM_IMAGE", "AVAILABLE"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insert(jdbc, household, account, "bad-purpose", "ITEM_DEFINITION", "SCRIPT", "AVAILABLE"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insert(jdbc, household, account, "bad-status", "ITEM_DEFINITION", "ITEM_IMAGE", "READY"))
                .isInstanceOf(DataAccessException.class);
    }

    private void insert(JdbcTemplate jdbc, UUID household, UUID account, String storageKey,
                        String ownerType, String purpose, String status) {
        jdbc.update("""
                insert into attachment(id,household_id,owner_type,owner_id,purpose,original_filename,
                    storage_key,detected_media_type,size_bytes,sha256,status,uploaded_by,request_id)
                values (?,?,?,?,?,'file.jpg',?,'image/jpeg',10,repeat('a',64),?,?, 'request-123')
                """, UUID.randomUUID(), household, ownerType, ownerId, purpose, storageKey, status, account);
    }
}
