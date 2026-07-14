package com.stocket.identity;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.web.context.WebApplicationContext;
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

/**
 * Tests that database-backed sessions persist across application context restarts.
 * This verifies that sessions are stored in the database and can be used after
 * the application restarts (e.g., after a deployment or crash).
 */
@Testcontainers
class SessionPersistenceTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Test
    void sessionPersistsAcrossContextRestart() throws Exception {
        // Get the database connection details from the Testcontainer
        String jdbcUrl = postgres.getJdbcUrl();
        String username = postgres.getUsername();
        String password = postgres.getPassword();

        // First context: create a session
        String sessionCookie;
        try (ConfigurableApplicationContext firstContext = new SpringApplicationBuilder(com.stocket.StocketApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties(
                        "spring.datasource.url=" + jdbcUrl,
                        "spring.datasource.username=" + username,
                        "spring.datasource.password=" + password,
                        "spring.jpa.hibernate.ddl-auto=validate",
                        "spring.flyway.enabled=true")
                .run()) {

            // Create MockMvc manually from the WebApplicationContext
            WebApplicationContext webAppContext = (WebApplicationContext) firstContext;
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext)
                    .apply(SecurityMockMvcConfigurers.springSecurity())
                    .build();

            // Initialize the system (creates admin account and session)
            MvcResult result = mockMvc.perform(post("/api/v1/setup/initialize")
                            .with(csrf())
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {"householdName":"王家","timezone":"Asia/Shanghai",
                                     "username":"Owner","displayName":"管理员","password":"correct horse battery staple"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(cookie().exists("STOCKET_SESSION"))
                    .andReturn();

            sessionCookie = result.getResponse().getCookie("STOCKET_SESSION").getValue();

            // Verify the session works in the first context
            mockMvc.perform(get("/api/v1/account")
                            .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", sessionCookie)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("Owner"));

            // Verify session is stored in the database
            JdbcTemplate jdbc = firstContext.getBean(JdbcTemplate.class);
            Integer sessionCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM user_session WHERE revoked_at IS NULL",
                    Integer.class);
            assertThat(sessionCount).isEqualTo(1);

            // First context will be closed here (try-with-resources)
        }

        // Second context: use the same cookie to access the account
        try (ConfigurableApplicationContext secondContext = new SpringApplicationBuilder(com.stocket.StocketApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties(
                        "spring.datasource.url=" + jdbcUrl,
                        "spring.datasource.username=" + username,
                        "spring.datasource.password=" + password,
                        "spring.jpa.hibernate.ddl-auto=validate",
                        "spring.flyway.enabled=false") // Disable Flyway for second context (migrations already applied)
                .run()) {

            // Create MockMvc manually from the WebApplicationContext
            WebApplicationContext webAppContext = (WebApplicationContext) secondContext;
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext)
                    .apply(SecurityMockMvcConfigurers.springSecurity())
                    .build();

            // Use the original cookie to access a protected endpoint - should succeed
            // (200 means auth passed, 401 would mean auth failed)
            mockMvc.perform(get("/api/v1/account")
                            .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", sessionCookie)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("Owner"));

            // Verify session is still in the database
            JdbcTemplate jdbc = secondContext.getBean(JdbcTemplate.class);
            Integer sessionCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM user_session WHERE revoked_at IS NULL",
                    Integer.class);
            assertThat(sessionCount).isEqualTo(1);

            // Second context will be closed here (try-with-resources)
        }
    }
}
