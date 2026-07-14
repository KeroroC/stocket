package com.stocket.notification;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.stocket.identity.internal.authentication.SecureValueGenerator;
import com.stocket.identity.internal.authentication.TokenHasher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = "STOCKET_MASTER_KEY=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DisabledInAotMode
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChannelApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired DataSource dataSource;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired SecureValueGenerator values;
    @Autowired TokenHasher hasher;

    JdbcTemplate jdbc;
    UUID householdId;
    String adminSession;
    String viewerSession;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("truncate user_account, household cascade");
        householdId = UUID.randomUUID();
        jdbc.update("insert into household(id,singleton_key,name,timezone) values (?,1,'王家','Asia/Shanghai')",
                householdId);
        adminSession = createSession("channel-admin", "ADMIN");
        viewerSession = createSession("channel-viewer", "VIEWER");
    }

    @Test
    void encryptsSecretsNeverReturnsThemAndProtectsConfigurationOperations() throws Exception {
        String create = """
                {"enabled":true,"version":0,
                 "configuration":{"host":"smtp.example.com","port":587,"tlsMode":"STARTTLS",
                                  "username":"mailer","fromAddress":"stocket@example.com"},
                 "secret":"smtp-password"}
                """;
        String body = mockMvc.perform(put("/api/v1/notification/channels/SMTP")
                        .with(csrf()).cookie(cookie(adminSession)).contentType(APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SMTP"))
                .andExpect(jsonPath("$.hasSecret").value(true))
                .andExpect(jsonPath("$.secret").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("smtp-password");

        UUID channelId = jdbc.queryForObject(
                "select id from notification_channel where household_id=? and type='SMTP'",
                UUID.class, householdId);
        String encryptedBefore = jdbc.queryForObject(
                "select encrypted_secret from notification_channel where id=?", String.class, channelId);
        assertThat(encryptedBefore).doesNotContain("smtp-password");

        mockMvc.perform(put("/api/v1/notification/channels/SMTP")
                        .with(csrf()).cookie(cookie(adminSession)).contentType(APPLICATION_JSON)
                        .content("""
                                {"enabled":false,"version":0,
                                 "configuration":{"host":"smtp.example.com","port":465,"tlsMode":"TLS",
                                                  "username":"mailer","fromAddress":"stocket@example.com"},
                                 "secret":""}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasSecret").value(true))
                .andExpect(jsonPath("$.version").value(1));
        assertThat(jdbc.queryForObject(
                "select encrypted_secret from notification_channel where id=?", String.class, channelId))
                .isEqualTo(encryptedBefore);

        mockMvc.perform(get("/api/v1/notification/channels").cookie(cookie(adminSession)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("smtp-password"))));
        mockMvc.perform(put("/api/v1/notification/channels/SMTP")
                        .with(csrf()).cookie(cookie(viewerSession)).contentType(APPLICATION_JSON).content(create))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/notification/channels/WEBHOOK")
                        .with(csrf()).cookie(cookie(adminSession)).contentType(APPLICATION_JSON)
                        .content("""
                                {"enabled":true,"version":0,
                                 "configuration":{"url":"https://127.0.0.1/internal"},"secret":"hook"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CHANNEL_INVALID"));

        mockMvc.perform(post("/api/v1/notification/channels/{id}/test", channelId)
                        .with(csrf()).cookie(cookie(adminSession)))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/v1/notification/channels/{id}/test", channelId)
                        .with(csrf()).cookie(cookie(adminSession)))
                .andExpect(status().isTooManyRequests());
    }

    private String createSession(String username, String role) {
        UUID accountId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        jdbc.update("""
                insert into user_account(id,username,normalized_username,display_name,password_hash,status,
                    must_change_password,credentials_changed_at,created_at,updated_at,version)
                values (?,?,?,?,?,'ACTIVE',false,now(),now(),now(),0)
                """, accountId, username, username, username,
                passwordEncoder.encode("correct horse battery staple"));
        jdbc.update("""
                insert into household_member(id,household_id,account_id,role,created_at,updated_at)
                values (?,?,?,?,now(),now())
                """, memberId, householdId, accountId, role);
        String token = values.generateToken();
        Instant now = Instant.now();
        jdbc.update("""
                insert into user_session(id,account_id,token_hash,created_at,last_seen_at,idle_expires_at,
                    absolute_expires_at)
                values (?,?,?,?::timestamptz,?::timestamptz,?::timestamptz,?::timestamptz)
                """, UUID.randomUUID(), accountId, hasher.sha256(token), now.toString(), now.toString(),
                now.plus(30, ChronoUnit.DAYS).toString(), now.plus(90, ChronoUnit.DAYS).toString());
        return token;
    }

    private jakarta.servlet.http.Cookie cookie(String token) {
        return new jakarta.servlet.http.Cookie("STOCKET_SESSION", token);
    }
}
