package com.stocket.catalog;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.sql.DataSource;

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

import com.jayway.jsonpath.JsonPath;
import com.stocket.identity.internal.authentication.SecureValueGenerator;
import com.stocket.identity.internal.authentication.TokenHasher;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@DisabledInAotMode
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class CategoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SecureValueGenerator secureValueGenerator;

    @Autowired
    private TokenHasher tokenHasher;

    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE user_account, household CASCADE");
    }

    @Test
    void adminCreatesFlatOrderedCategoryTree() throws Exception {
        String adminCookie = initializeAdmin();
        UUID foodId = createCategory(adminCookie, "食品", null);
        createCategory(adminCookie, "冷藏食品", foodId);
        createCategory(adminCookie, "日用品", null);

        mockMvc.perform(get("/api/v1/categories")
                        .cookie(sessionCookie(adminCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].name").value("日用品"))
                .andExpect(jsonPath("$[1].name").value("食品"))
                .andExpect(jsonPath("$[2].name").value("冷藏食品"))
                .andExpect(jsonPath("$[2].parentId").value(foodId.toString()))
                .andExpect(jsonPath("$[0].archived").value(false));
    }

    @Test
    void rejectsDuplicateSiblingAndCategoryCycle() throws Exception {
        String adminCookie = initializeAdmin();
        UUID foodId = createCategory(adminCookie, "食品", null);
        UUID chilledId = createCategory(adminCookie, "冷藏食品", foodId);

        mockMvc.perform(post("/api/v1/categories")
                        .with(csrf())
                        .cookie(sessionCookie(adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":" 食品 ","defaultInventoryType":"BATCH","attributeSchema":[]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_NAME_CONFLICT"));

        mockMvc.perform(patch("/api/v1/categories/{id}", foodId)
                        .with(csrf())
                        .cookie(sessionCookie(adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"食品","parentId":"%s","defaultInventoryType":"BATCH",
                                 "attributeSchema":[],"version":0}
                                """.formatted(chilledId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_CYCLE"));
    }

    @Test
    void memberCanReadButCannotMaintainCategories() throws Exception {
        String adminCookie = initializeAdmin();
        createCategory(adminCookie, "食品", null);
        String memberCookie = createMemberSession("MEMBER");

        mockMvc.perform(get("/api/v1/categories").cookie(sessionCookie(memberCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(post("/api/v1/categories")
                        .with(csrf())
                        .cookie(sessionCookie(memberCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"无权限","defaultInventoryType":"BATCH","attributeSchema":[]}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownCategoryIsHiddenAsNotFound() throws Exception {
        String adminCookie = initializeAdmin();

        mockMvc.perform(patch("/api/v1/categories/{id}", UUID.randomUUID())
                        .with(csrf())
                        .cookie(sessionCookie(adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"不存在","defaultInventoryType":"BATCH",
                                 "attributeSchema":[],"version":0}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void rejectsSchemaChangesThatInvalidateActiveItems() throws Exception {
        String adminCookie = initializeAdmin();
        UUID householdId = jdbc.queryForObject("select id from household", UUID.class);
        UUID categoryId = createCategory(adminCookie, "食品", null);
        UUID itemId = UUID.randomUUID();
        jdbc.update("""
                insert into item_definition(id, household_id, category_id, name, normalized_name,
                    default_unit, custom_attributes)
                values (?, ?, ?, '牛奶', '牛奶', '盒', '{"temperature":4}'::jsonb)
                """, itemId, householdId, categoryId);

        mockMvc.perform(patch("/api/v1/categories/{id}", categoryId)
                        .with(csrf())
                        .cookie(sessionCookie(adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"食品","defaultInventoryType":"BATCH","version":0,
                                 "attributeSchema":[{"key":"opened","label":"已开封","type":"BOOLEAN",
                                     "required":true,"options":[],"order":0}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTRIBUTE_SCHEMA_INCOMPATIBLE"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(itemId.toString())))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("temperature")));
    }

    private UUID createCategory(String cookie, String name, UUID parentId) throws Exception {
        String parent = parentId == null ? "null" : "\"" + parentId + "\"";
        MvcResult result = mockMvc.perform(post("/api/v1/categories")
                        .with(csrf())
                        .cookie(sessionCookie(cookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","parentId":%s,"defaultInventoryType":"BATCH",
                                 "attributeSchema":[]}
                                """.formatted(name, parent)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private String initializeAdmin() {
        UUID householdId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        jdbc.update("insert into household(id, singleton_key, name, timezone) values (?, 1, '王家', 'Asia/Shanghai')",
                householdId);
        jdbc.update("""
                insert into user_account(id, username, normalized_username, display_name, password_hash,
                    status, must_change_password, credentials_changed_at, created_at, updated_at, version)
                values (?, 'Owner', 'owner', '管理员', ?, 'ACTIVE', false, now(), now(), now(), 0)
                """, accountId, passwordEncoder.encode("correct horse battery staple"));
        jdbc.update("""
                insert into household_member(id, household_id, account_id, role, created_at, updated_at)
                values (?, ?, ?, 'ADMIN', now(), now())
                """, UUID.randomUUID(), householdId, accountId);
        return createSession(accountId);
    }

    private String createMemberSession(String role) {
        UUID householdId = jdbc.queryForObject("select id from household", UUID.class);
        UUID accountId = UUID.randomUUID();
        String username = "member" + accountId.toString().substring(0, 8);
        jdbc.update("""
                insert into user_account(id, username, normalized_username, display_name, password_hash,
                    status, must_change_password, credentials_changed_at, created_at, updated_at, version)
                values (?, ?, ?, '成员', ?, 'ACTIVE', false, now(), now(), now(), 0)
                """, accountId, username, username, passwordEncoder.encode("member-password"));
        jdbc.update("""
                insert into household_member(id, household_id, account_id, role, created_at, updated_at)
                values (?, ?, ?, ?, now(), now())
                """, UUID.randomUUID(), householdId, accountId, role);

        return createSession(accountId);
    }

    private String createSession(UUID accountId) {
        String token = secureValueGenerator.generateToken();
        Instant now = Instant.now();
        jdbc.update("""
                insert into user_session(id, account_id, token_hash, created_at, last_seen_at,
                    idle_expires_at, absolute_expires_at, user_agent, source_address)
                values (?, ?, ?, ?::timestamptz, ?::timestamptz, ?::timestamptz, ?::timestamptz,
                    'test', '127.0.0.1')
                """, UUID.randomUUID(), accountId, tokenHasher.sha256(token), now.toString(), now.toString(),
                now.plus(30, ChronoUnit.DAYS).toString(), now.plus(90, ChronoUnit.DAYS).toString());
        return token;
    }

    private jakarta.servlet.http.Cookie sessionCookie(String value) {
        return new jakarta.servlet.http.Cookie("STOCKET_SESSION", value);
    }
}
