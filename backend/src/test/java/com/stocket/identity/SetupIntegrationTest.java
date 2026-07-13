package com.stocket.identity;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@DisabledInAotMode
class SetupIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Test
    void emptyDatabaseShowsNotInitialized() throws Exception {
        // Clean all tables for this test
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE household_member, user_session, member_invite, user_account, household CASCADE");

        mockMvc.perform(get("/api/v1/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialized").value(false));
    }

    @Test
    void initializeCreatesThreeRelatedRecords() throws Exception {
        // Clean all tables for this test
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE household_member, user_session, member_invite, user_account, household CASCADE");

        mockMvc.perform(post("/api/v1/setup/initialize")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"householdName":"王家","timezone":"Asia/Shanghai",
                                 "username":"Owner","displayName":"管理员","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(cookie().exists("STOCKET_SESSION"))
                .andExpect(jsonPath("$.username").value("Owner"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // Verify three records were created
        assertThat(jdbc.queryForObject("SELECT count(*) FROM household", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_account", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM household_member", Integer.class)).isEqualTo(1);
    }

    @Test
    void duplicateUsernameNormalizedReturnsConflict() throws Exception {
        // Clean all tables for this test
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE household_member, user_session, member_invite, user_account, household CASCADE");

        // First setup
        mockMvc.perform(post("/api/v1/setup/initialize")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"householdName":"王家","timezone":"Asia/Shanghai",
                                 "username":"Owner","displayName":"管理员","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isCreated());

        // Second setup with same username (different case/whitespace)
        mockMvc.perform(post("/api/v1/setup/initialize")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"householdName":"Another","timezone":"Asia/Shanghai",
                                 "username":"  owner  ","displayName":"Another","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETUP_ALREADY_COMPLETED"));
    }

    @Test
    void secondInitializeReturnsConflict() throws Exception {
        // Clean all tables for this test
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE household_member, user_session, member_invite, user_account, household CASCADE");

        // First setup
        mockMvc.perform(post("/api/v1/setup/initialize")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"householdName":"王家","timezone":"Asia/Shanghai",
                                 "username":"Owner","displayName":"管理员","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isCreated());

        // Second setup
        mockMvc.perform(post("/api/v1/setup/initialize")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"householdName":"Another","timezone":"Asia/Shanghai",
                                 "username":"Another","displayName":"Another","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETUP_ALREADY_COMPLETED"));
    }

    @Test
    void concurrentInitializeExactlyOneSucceeds() throws Exception {
        // Clean all tables for this test
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE household_member, user_session, member_invite, user_account, household CASCADE");

        int threadCount = 3;
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CopyOnWriteArrayList<Integer> statusCodes = new CopyOnWriteArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?>[] futures = new Future<?>[threadCount];
            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                futures[i] = executor.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();
                        int statusCode = mockMvc.perform(post("/api/v1/setup/initialize")
                                        .with(csrf())
                                        .contentType(APPLICATION_JSON)
                                        .content("""
                                                {"householdName":"王家","timezone":"Asia/Shanghai",
                                                 "username":"User%d","displayName":"管理员%d",
                                                 "password":"correct horse battery staple%d"}
                                                """.formatted(idx, idx, idx)))
                                .andReturn()
                                .getResponse()
                                .getStatus();
                        statusCodes.add(statusCode);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            readyLatch.await();
            startLatch.countDown();

            for (Future<?> future : futures) {
                future.get();
            }
        }

        assertThat(statusCodes).hasSize(threadCount);
        assertThat(statusCodes.stream().filter(code -> code == 201).count()).isEqualTo(1);
        assertThat(statusCodes.stream().filter(code -> code == 409).count()).isEqualTo(threadCount - 1);
    }
}
