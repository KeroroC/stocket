package com.stocket.attachment;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import javax.sql.DataSource;

import com.stocket.attachment.internal.recovery.AttachmentRecoveryJob;
import com.stocket.attachment.internal.storage.LocalAttachmentStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@DisabledInAotMode
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class AttachmentRecoveryIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");
    static final Path attachmentDir = temp();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("stocket.attachment.directory", () -> attachmentDir.toString()); }
    @Autowired DataSource dataSource;
    @Autowired LocalAttachmentStore store;
    @Autowired AttachmentRecoveryJob recovery;

    @Test void promotesRecoverableStagedFileAndMarksMissingAvailableFile() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID household = UUID.randomUUID(), account = UUID.randomUUID(), owner = UUID.randomUUID();
        jdbc.update("insert into household(id,singleton_key,name,timezone) values (?,1,'家','Asia/Shanghai')", household);
        jdbc.update("insert into user_account(id,username,normalized_username,display_name,password_hash,status,must_change_password,credentials_changed_at,created_at,updated_at,version) values (?,'u','u','U','h','ACTIVE',false,now(),now(),now(),0)", account);
        var staged = store.stage(new ByteArrayInputStream("recover".getBytes()));
        insert(jdbc, household, account, owner, staged.storageKey(), "STAGED");
        String missingKey = "b".repeat(64); insert(jdbc, household, account, owner, missingKey, "AVAILABLE");
        var deleted = store.stage(new ByteArrayInputStream("deleted".getBytes()));
        store.commit(deleted);
        insert(jdbc, household, account, owner, deleted.storageKey(), "DELETED");

        recovery.recover();

        assertThat(jdbc.queryForObject("select status from attachment where storage_key=?", String.class, staged.storageKey())).isEqualTo("AVAILABLE");
        assertThat(store.exists(staged.storageKey())).isTrue();
        assertThat(jdbc.queryForObject("select status from attachment where storage_key=?", String.class, missingKey)).isEqualTo("MISSING");
        assertThat(store.exists(deleted.storageKey())).isFalse();
    }

    private void insert(JdbcTemplate jdbc, UUID household, UUID account, UUID owner, String key, String status) {
        jdbc.update("insert into attachment(id,household_id,owner_type,owner_id,purpose,original_filename,storage_key,detected_media_type,size_bytes,sha256,status,uploaded_by,request_id) values (?,?, 'ITEM_DEFINITION',?,'ITEM_IMAGE','x.png',?,'image/png',7,repeat('a',64),?,?, 'recovery-request')", UUID.randomUUID(), household, owner, key, status, account);
    }
    private static Path temp() { try { return Files.createTempDirectory("stocket-attachment-recovery-"); } catch (Exception e) { throw new RuntimeException(e); } }
}
