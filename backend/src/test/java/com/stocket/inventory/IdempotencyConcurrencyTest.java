package com.stocket.inventory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import com.stocket.identity.internal.authentication.SecureValueGenerator;
import com.stocket.identity.internal.authentication.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@DisabledInAotMode
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class IdempotencyConcurrencyTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired DataSource dataSource;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired SecureValueGenerator values;
    @Autowired TokenHasher hasher;

    private JdbcTemplate jdbc;
    private UUID householdId;
    private UUID itemId;
    private UUID locationId;
    private String session;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("truncate user_account, household cascade");
        householdId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        locationId = UUID.randomUUID();
        jdbc.update("insert into household(id,singleton_key,name,timezone) values (?,1,'王家','Asia/Shanghai')",
                householdId);
        jdbc.update("""
                insert into item_definition(id,household_id,name,normalized_name,default_unit,custom_attributes)
                values (?,?,'牛奶','牛奶','盒','{}'::jsonb)
                """, itemId, householdId);
        jdbc.update("""
                insert into location(id,household_id,name,normalized_name,public_code)
                values (?,?,'冰箱','冰箱',?)
                """, locationId, householdId, UUID.randomUUID().toString());
        session = createSession();
    }

    @Test
    void concurrentSameKeyCreatesOneEntryAndOneMovement() throws Exception {
        int requests = 20;
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();

        try (var executor = Executors.newFixedThreadPool(requests)) {
            for (int index = 0; index < requests; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return performReceive("concurrent-key");
                }));
            }
            ready.await();
            start.countDown();

            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get());
            }
            assertThat(results).allSatisfy(result ->
                    assertThat(result.getResponse().getStatus()).isIn(201, 409));
            assertThat(results).anySatisfy(result ->
                    assertThat(result.getResponse().getStatus()).isEqualTo(201));
        }

        MvcResult replay = performReceive("concurrent-key");
        assertThat(replay.getResponse().getStatus()).isEqualTo(201);
        assertThat(jdbc.queryForObject("select count(*) from inventory_entry", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from inventory_movement", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from idempotency_record where status='COMPLETED'",
                Integer.class)).isEqualTo(1);
    }

    private MvcResult performReceive(String key) throws Exception {
        return mockMvc.perform(post("/api/v1/inventory/receipts").with(csrf()).cookie(cookie(session))
                        .header("Idempotency-Key", key).contentType(APPLICATION_JSON).content("""
                                {"itemId":"%s","type":"BATCH","quantity":"2","locationId":"%s",
                                 "receivedAt":"2026-07-14T00:00:00Z","batchNumber":"B-001",
                                 "customAttributes":{}}
                                """.formatted(itemId, locationId)))
                .andReturn();
    }

    private String createSession() {
        UUID accountId = UUID.randomUUID();
        jdbc.update("""
                insert into user_account(id,username,normalized_username,display_name,password_hash,status,
                    must_change_password,credentials_changed_at,created_at,updated_at,version)
                values (?,'member','member','member',?,'ACTIVE',false,now(),now(),now(),0)
                """, accountId, passwordEncoder.encode("correct horse battery staple"));
        jdbc.update("""
                insert into household_member(id,household_id,account_id,role,created_at,updated_at)
                values (?,?,?,'MEMBER',now(),now())
                """, UUID.randomUUID(), householdId, accountId);
        String token = values.generateToken();
        Instant now = Instant.now();
        jdbc.update("""
                insert into user_session(id,account_id,token_hash,created_at,last_seen_at,idle_expires_at,absolute_expires_at)
                values (?,?,?,?::timestamptz,?::timestamptz,?::timestamptz,?::timestamptz)
                """, UUID.randomUUID(), accountId, hasher.sha256(token), now.toString(), now.toString(),
                now.plus(30, ChronoUnit.DAYS).toString(), now.plus(90, ChronoUnit.DAYS).toString());
        return token;
    }

    private jakarta.servlet.http.Cookie cookie(String token) {
        return new jakarta.servlet.http.Cookie("STOCKET_SESSION", token);
    }
}
