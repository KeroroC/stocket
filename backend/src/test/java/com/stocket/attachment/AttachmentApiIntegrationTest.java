package com.stocket.attachment;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.sql.DataSource;

import com.jayway.jsonpath.JsonPath;
import com.stocket.identity.internal.authentication.SecureValueGenerator;
import com.stocket.identity.internal.authentication.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class AttachmentApiIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");
    static final Path attachmentDir = createTempDirectory();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("stocket.attachment.directory", () -> attachmentDir.toString());
    }

    @Autowired MockMvc mockMvc;
    @Autowired DataSource dataSource;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired SecureValueGenerator values;
    @Autowired TokenHasher hasher;
    JdbcTemplate jdbc;
    UUID householdId;
    UUID itemId;

    @BeforeEach void setUp() throws Exception {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("truncate user_account, household cascade");
        householdId = UUID.randomUUID(); itemId = UUID.randomUUID();
        UUID category = UUID.randomUUID();
        jdbc.update("insert into household(id,singleton_key,name,timezone) values (?,1,'家','Asia/Shanghai')", householdId);
        jdbc.update("insert into category(id,household_id,name,normalized_name,default_inventory_type,attribute_schema) values (?,?,'食品','食品','BATCH','[]'::jsonb)", category, householdId);
        jdbc.update("insert into item_definition(id,household_id,category_id,name,normalized_name,default_unit,custom_attributes) values (?,?,?,'牛奶','牛奶','盒','{}'::jsonb)", itemId, householdId, category);
    }

    @Test void memberLifecycleViewerReadOnlyCoverReplacementAndRange() throws Exception {
        String member = createSession("member", "MEMBER");
        UUID first = upload(member, "COVER_IMAGE");
        assertThat(jdbc.queryForObject("select request_id from attachment where id=?", String.class, first))
                .isEqualTo("attachment-test-request");
        assertThat(jdbc.queryForObject("select count(*) from audit_log where event_type='AttachmentUploaded' and subject_id=? and request_id='attachment-test-request'", Integer.class, first))
                .isEqualTo(1);
        mockMvc.perform(get("/api/v1/attachments/{id}", first).cookie(cookie(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.purpose").value("COVER_IMAGE"));
        MvcResult pendingContent = mockMvc.perform(get("/api/v1/attachments/{id}/content", first).cookie(cookie(member)))
                .andExpect(request().asyncStarted()).andReturn();
        MvcResult content = mockMvc.perform(asyncDispatch(pendingContent))
                .andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andReturn();
        assertThat(content.getResponse().getContentAsByteArray()).isNotEmpty();
        MvcResult pendingRange = mockMvc.perform(get("/api/v1/attachments/{id}/content", first).cookie(cookie(member)).header("Range", "bytes=0-7"))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(pendingRange)).andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", org.hamcrest.Matchers.startsWith("bytes 0-7/")));
        mockMvc.perform(get("/api/v1/attachments/{id}/content", first).cookie(cookie(member)).header("Range", "bytes=999999-"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_RANGE_INVALID"));

        UUID second = upload(member, "COVER_IMAGE");
        assertThat(jdbc.queryForObject("select count(*) from attachment where purpose='COVER_IMAGE' and status='AVAILABLE'", Integer.class)).isEqualTo(1);
        assertThat(second).isNotEqualTo(first);

        String viewer = createSession("viewer", "VIEWER");
        MvcResult viewerDownload = mockMvc.perform(get("/api/v1/attachments/{id}/content", second).cookie(cookie(viewer)))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(viewerDownload)).andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/attachments").file(file()).with(csrf()).cookie(cookie(viewer))
                .param("ownerType", "ITEM_DEFINITION").param("ownerId", itemId.toString()).param("purpose", "ITEM_IMAGE"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(delete("/api/v1/attachments/{id}", second).with(csrf()).cookie(cookie(member))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/attachments/{id}/content", second).cookie(cookie(member))).andExpect(status().isNotFound());
        jdbc.update("update item_definition set archived_at=now() where id=?", itemId);
        mockMvc.perform(multipart("/api/v1/attachments").file(file()).with(csrf()).cookie(cookie(member))
                .param("ownerType", "ITEM_DEFINITION").param("ownerId", itemId.toString()).param("purpose", "ITEM_IMAGE"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTACHMENT_OWNER_ARCHIVED"));
    }

    private UUID upload(String token, String purpose) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/attachments").file(file()).with(csrf()).cookie(cookie(token))
                        .param("ownerType", "ITEM_DEFINITION").param("ownerId", itemId.toString()).param("purpose", purpose)
                        .header("X-Request-Id", "attachment-test-request"))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private MockMultipartFile file() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream(); ImageIO.write(image, "png", output);
        return new MockMultipartFile("file", "cover.png", "image/png", output.toByteArray());
    }

    private String createSession(String username, String role) {
        UUID account = UUID.randomUUID();
        jdbc.update("insert into user_account(id,username,normalized_username,display_name,password_hash,status,must_change_password,credentials_changed_at,created_at,updated_at,version) values (?,?,?,?,?,'ACTIVE',false,now(),now(),now(),0)", account, username, username, username, passwordEncoder.encode("correct horse battery staple"));
        jdbc.update("insert into household_member(id,household_id,account_id,role,created_at,updated_at) values (?,?,?,?,now(),now())", UUID.randomUUID(), householdId, account, role);
        String token = values.generateToken(); Instant now = Instant.now();
        jdbc.update("insert into user_session(id,account_id,token_hash,created_at,last_seen_at,idle_expires_at,absolute_expires_at) values (?,?,?,?::timestamptz,?::timestamptz,?::timestamptz,?::timestamptz)", UUID.randomUUID(), account, hasher.sha256(token), now.toString(), now.toString(), now.plus(30, ChronoUnit.DAYS).toString(), now.plus(90, ChronoUnit.DAYS).toString());
        return token;
    }
    private jakarta.servlet.http.Cookie cookie(String token) { return new jakarta.servlet.http.Cookie("STOCKET_SESSION", token); }
    private static Path createTempDirectory() { try { return Files.createTempDirectory("stocket-attachment-api-"); } catch (Exception e) { throw new RuntimeException(e); } }
}
