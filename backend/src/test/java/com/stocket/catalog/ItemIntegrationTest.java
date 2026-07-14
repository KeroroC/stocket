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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
class ItemIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private DataSource dataSource;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SecureValueGenerator secureValueGenerator;
    @Autowired private TokenHasher tokenHasher;

    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE user_account, household CASCADE");
    }

    @Test
    void createsAndReadsItemWithDefaultAttributesBarcodesAndTags() throws Exception {
        String adminCookie = initializeAdmin();
        UUID categoryId = createCategory(adminCookie, "食品", "[]");
        jdbc.update("""
                update category set attribute_schema =
                    '[{"key":"storage","label":"储存方式","type":"TEXT","required":true,
                       "defaultValue":"冷藏","options":[],"order":0}]'::jsonb
                where id = ?
                """, categoryId);

        MvcResult result = createItem(adminCookie, categoryId, """
                "barcodes":["  abc-123  ","6901234567890"],
                "tags":["  早餐 ","乳制品"]
                """);
        UUID itemId = responseId(result);

        mockMvc.perform(get("/api/v1/items/{id}", itemId).cookie(sessionCookie(adminCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("鲜牛奶"))
                .andExpect(jsonPath("$.categoryId").value(categoryId.toString()))
                .andExpect(jsonPath("$.defaultUnit").value("盒"))
                .andExpect(jsonPath("$.customAttributes.storage").value("冷藏"))
                .andExpect(jsonPath("$.barcodes", containsInAnyOrder("abc-123", "6901234567890")))
                .andExpect(jsonPath("$.tags", containsInAnyOrder("早餐", "乳制品")))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.version").value(0));

        assertThat(jdbc.queryForObject(
                "select normalized_value from item_barcode where raw_value = 'abc-123'", String.class))
                .isEqualTo("ABC-123");
    }

    @Test
    void duplicateBarcodeRollsBackEntireCreate() throws Exception {
        String adminCookie = initializeAdmin();
        UUID categoryId = createCategory(adminCookie, "食品", "[]");
        createItem(adminCookie, categoryId, "\"barcodes\":[\"abc-123\"],\"tags\":[]");
        int before = jdbc.queryForObject("select count(*) from item_definition", Integer.class);

        mockMvc.perform(post("/api/v1/items")
                        .with(csrf()).cookie(sessionCookie(adminCookie)).contentType(APPLICATION_JSON)
                        .content(itemJson(categoryId,
                                "\"barcodes\":[\"new-code\",\" ABC-123 \"],\"tags\":[\"new-tag\"]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BARCODE_CONFLICT"));

        assertThat(jdbc.queryForObject("select count(*) from item_definition", Integer.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject(
                "select count(*) from item_barcode where normalized_value = 'NEW-CODE'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from item_tag where normalized_value = 'new-tag'", Integer.class)).isZero();
    }

    @Test
    void rejectsDuplicateBarcodeWithinRequestAndStaleVersion() throws Exception {
        String adminCookie = initializeAdmin();
        UUID categoryId = createCategory(adminCookie, "食品", "[]");

        mockMvc.perform(post("/api/v1/items")
                        .with(csrf()).cookie(sessionCookie(adminCookie)).contentType(APPLICATION_JSON)
                        .content(itemJson(categoryId,
                                "\"barcodes\":[\"abc\",\" ABC \"],\"tags\":[]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BARCODE_CONFLICT"));
        assertThat(jdbc.queryForObject("select count(*) from item_definition", Integer.class)).isZero();

        UUID itemId = responseId(createItem(adminCookie, categoryId,
                "\"barcodes\":[\"xyz\"],\"tags\":[]"));
        mockMvc.perform(patch("/api/v1/items/{id}", itemId)
                        .with(csrf()).cookie(sessionCookie(adminCookie)).contentType(APPLICATION_JSON)
                        .content(itemJson(categoryId,
                                "\"barcodes\":[\"xyz\"],\"tags\":[],\"version\":99")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void updateReplacesBarcodesAndTagsInOneTransaction() throws Exception {
        String adminCookie = initializeAdmin();
        UUID categoryId = createCategory(adminCookie, "食品", "[]");
        UUID itemId = responseId(createItem(adminCookie, categoryId,
                "\"barcodes\":[\"old-code\"],\"tags\":[\"旧标签\"]"));

        mockMvc.perform(patch("/api/v1/items/{id}", itemId)
                        .with(csrf()).cookie(sessionCookie(adminCookie)).contentType(APPLICATION_JSON)
                        .content(itemJson(categoryId,
                                "\"barcodes\":[\"new-code\"],\"tags\":[\"新标签\"],\"version\":0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barcodes", containsInAnyOrder("new-code")))
                .andExpect(jsonPath("$.tags", containsInAnyOrder("新标签")))
                .andExpect(jsonPath("$.version").value(1));

        assertThat(jdbc.queryForObject(
                "select count(*) from item_barcode where item_definition_id = ? and raw_value = 'old-code'",
                Integer.class, itemId)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from item_tag where item_definition_id = ? and value = '旧标签'",
                Integer.class, itemId)).isZero();
    }

    @Test
    void memberCanWriteAndViewerCanOnlyRead() throws Exception {
        String adminCookie = initializeAdmin();
        UUID categoryId = createCategory(adminCookie, "日用品", "[]");
        String memberCookie = createMemberSession("MEMBER");
        String viewerCookie = createMemberSession("VIEWER");

        UUID itemId = responseId(createItem(memberCookie, categoryId,
                "\"barcodes\":[\"member-code\"],\"tags\":[]"));
        mockMvc.perform(get("/api/v1/items/{id}", itemId).cookie(sessionCookie(viewerCookie)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/items")
                        .with(csrf()).cookie(sessionCookie(viewerCookie)).contentType(APPLICATION_JSON)
                        .content(itemJson(categoryId, "\"barcodes\":[],\"tags\":[]")))
                .andExpect(status().isForbidden());
    }

    @Test
    void archivedAndUnknownCategoriesCannotReceiveItems() throws Exception {
        String adminCookie = initializeAdmin();
        UUID categoryId = createCategory(adminCookie, "食品", "[]");
        mockMvc.perform(post("/api/v1/categories/{id}/archive", categoryId)
                        .queryParam("version", "0").with(csrf()).cookie(sessionCookie(adminCookie)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/items")
                        .with(csrf()).cookie(sessionCookie(adminCookie)).contentType(APPLICATION_JSON)
                        .content(itemJson(categoryId, "\"barcodes\":[],\"tags\":[]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_ARCHIVED"));
        mockMvc.perform(post("/api/v1/items")
                        .with(csrf()).cookie(sessionCookie(adminCookie)).contentType(APPLICATION_JSON)
                        .content(itemJson(UUID.randomUUID(), "\"barcodes\":[],\"tags\":[]")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void categoryWithActiveItemCannotBeArchived() throws Exception {
        String adminCookie = initializeAdmin();
        UUID categoryId = createCategory(adminCookie, "食品", "[]");
        createItem(adminCookie, categoryId, "\"barcodes\":[],\"tags\":[]");

        mockMvc.perform(post("/api/v1/categories/{id}/archive", categoryId)
                        .queryParam("version", "0").with(csrf()).cookie(sessionCookie(adminCookie)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_EMPTY"));
    }

    @Test
    void archivedItemKeepsBarcodeAndRestoreRequiresActiveCategory() throws Exception {
        String adminCookie = initializeAdmin();
        UUID categoryId = createCategory(adminCookie, "食品", "[]");
        UUID itemId = responseId(createItem(adminCookie, categoryId,
                "\"barcodes\":[\"reserved\"],\"tags\":[]"));
        mockMvc.perform(post("/api/v1/items/{id}/archive", itemId)
                        .queryParam("version", "0").with(csrf()).cookie(sessionCookie(adminCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
        mockMvc.perform(post("/api/v1/items")
                        .with(csrf()).cookie(sessionCookie(adminCookie)).contentType(APPLICATION_JSON)
                        .content(itemJson(categoryId, "\"barcodes\":[\"RESERVED\"],\"tags\":[]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BARCODE_CONFLICT"));
        mockMvc.perform(post("/api/v1/categories/{id}/archive", categoryId)
                        .queryParam("version", "0").with(csrf()).cookie(sessionCookie(adminCookie)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/items/{id}/restore", itemId)
                        .queryParam("version", "1").with(csrf()).cookie(sessionCookie(adminCookie)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_ARCHIVED"));
    }

    private MvcResult createItem(String cookie, UUID categoryId, String fields) throws Exception {
        return mockMvc.perform(post("/api/v1/items")
                        .with(csrf()).cookie(sessionCookie(cookie)).contentType(APPLICATION_JSON)
                        .content(itemJson(categoryId, fields)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String itemJson(UUID categoryId, String fields) {
        return """
                {"name":" 鲜牛奶 ","categoryId":"%s","brand":"晨光","model":"M1",
                 "specification":"250ml","defaultUnit":"盒","defaultShelfLifeValue":7,
                 "defaultShelfLifeUnit":"DAY","customAttributes":{},%s}
                """.formatted(categoryId, fields);
    }

    private UUID createCategory(String cookie, String name, String schema) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/categories")
                        .with(csrf()).cookie(sessionCookie(cookie)).contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","defaultInventoryType":"BATCH","attributeSchema":%s}
                                """.formatted(name, schema)))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    private UUID responseId(MvcResult result) throws Exception {
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private String initializeAdmin() {
        UUID householdId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        jdbc.update("insert into household(id, singleton_key, name, timezone) values (?, 1, '王家', 'Asia/Shanghai')",
                householdId);
        createAccount(accountId, "owner", "ADMIN", householdId);
        return createSession(accountId);
    }

    private String createMemberSession(String role) {
        UUID householdId = jdbc.queryForObject("select id from household", UUID.class);
        UUID accountId = UUID.randomUUID();
        createAccount(accountId, "member" + accountId.toString().substring(0, 8), role, householdId);
        return createSession(accountId);
    }

    private void createAccount(UUID accountId, String username, String role, UUID householdId) {
        jdbc.update("""
                insert into user_account(id, username, normalized_username, display_name, password_hash,
                    status, must_change_password, credentials_changed_at, created_at, updated_at, version)
                values (?, ?, ?, '成员', ?, 'ACTIVE', false, now(), now(), now(), 0)
                """, accountId, username, username, passwordEncoder.encode("correct horse battery staple"));
        jdbc.update("""
                insert into household_member(id, household_id, account_id, role, created_at, updated_at)
                values (?, ?, ?, ?, now(), now())
                """, UUID.randomUUID(), householdId, accountId, role);
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
