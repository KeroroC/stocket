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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.stocket.identity.internal.authentication.SecureValueGenerator;
import com.stocket.identity.internal.authentication.TokenHasher;
import com.jayway.jsonpath.JsonPath;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class CatalogSearchIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");
    @Autowired MockMvc mockMvc;
    @Autowired DataSource dataSource;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired SecureValueGenerator values;
    @Autowired TokenHasher hasher;
    private JdbcTemplate jdbc;
    private UUID householdId;
    private String token;

    @BeforeEach
    void setup() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("truncate user_account, household cascade");
        householdId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        jdbc.update("insert into household(id, singleton_key, name, timezone) values (?,1,'家','Asia/Shanghai')", householdId);
        jdbc.update("""
                insert into user_account(id, username, normalized_username, display_name, password_hash,
                    status, must_change_password, credentials_changed_at, created_at, updated_at, version)
                values (?, 'reader', 'reader', '读者', ?, 'ACTIVE', false, now(), now(), now(), 0)
                """, accountId, passwordEncoder.encode("correct horse battery staple"));
        jdbc.update("insert into household_member(id,household_id,account_id,role,created_at,updated_at) values (?,?,?,'ADMIN',now(),now())",
                UUID.randomUUID(), householdId, accountId);
        token = values.generateToken();
        Instant now = Instant.now();
        jdbc.update("""
                insert into user_session(id,account_id,token_hash,created_at,last_seen_at,idle_expires_at,absolute_expires_at)
                values (?,?,?,?::timestamptz,?::timestamptz,?::timestamptz,?::timestamptz)
                """, UUID.randomUUID(), accountId, hasher.sha256(token), now.toString(), now.toString(),
                now.plus(30, ChronoUnit.DAYS).toString(), now.plus(90, ChronoUnit.DAYS).toString());
        insertProjection("蒙牛纯牛奶", "6900000000012", false);
        insertProjection("伊利牛奶", "6900000000013", false);
        insertProjection("归档牛奶", "6900000000014", true);
    }

    @Test
    void exactBarcodeWinsAndTextSearchIsStable() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/search").queryParam("q", "6900000000012").cookie(cookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("蒙牛纯牛奶"))
                .andExpect(jsonPath("$.items[0].matchType").value("BARCODE_EXACT"));

        mockMvc.perform(get("/api/v1/catalog/search").queryParam("q", "  牛奶  ").cookie(cookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[*].name", contains("伊利牛奶", "蒙牛纯牛奶")))
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void emptyQueryListsAllActiveItemsByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/search").cookie(cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[*].name", contains("伊利牛奶", "蒙牛纯牛奶")))
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void itemChangesBuildProjectionWithFullCategoryPathAndNormalizedSearch() throws Exception {
        UUID foodId = createCategory("食品", null);
        UUID dairyId = createCategory("乳制品", foodId);
        mockMvc.perform(post("/api/v1/items").with(csrf()).cookie(cookie()).contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"  高钙   牛奶 ","categoryId":"%s","brand":"MENGNIU",
                                 "model":"A1","specification":"250ml","defaultUnit":"盒",
                                 "customAttributes":{},"barcodes":[" ab-c123 "],"tags":[" 早餐 "]}
                                """.formatted(dairyId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/catalog/search").queryParam("q", "  mengniu  ").cookie(cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("高钙 牛奶"))
                .andExpect(jsonPath("$.items[0].categoryPath").value("食品 / 乳制品"))
                .andExpect(jsonPath("$.items[0].tags[0]").value("早餐"))
                .andExpect(jsonPath("$.items[0].barcodes[0]").value("ab-c123"));

        mockMvc.perform(get("/api/v1/catalog/search").queryParam("q", "  AB-C123 ").cookie(cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].matchType").value("BARCODE_EXACT"));
    }

    @Test
    void archivedItemsAreHiddenByDefaultAndIndexExists() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/search").queryParam("q", "归档牛奶").cookie(cookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)));
        String index = jdbc.queryForObject("select indexdef from pg_indexes where indexname='gin_catalog_search_text'", String.class);
        org.assertj.core.api.Assertions.assertThat(index).contains("gin_trgm_ops");
    }

    private void insertProjection(String name, String barcode, boolean archived) {
        UUID itemId = UUID.randomUUID();
        jdbc.update("""
                insert into item_definition(id,household_id,name,normalized_name,default_unit,custom_attributes,archived_at)
                values (?,?,?,?,'盒','{}'::jsonb,case when ? then now() else null end)
                """, itemId, householdId, name, name, archived);
        jdbc.update("""
                insert into catalog_search_projection(item_definition_id,household_id,display_name,category_path,
                    tags,raw_barcodes,normalized_barcodes,searchable_text,archived)
                values (?,?,?,'食品',array['乳制品'],array[?],array[?],lower(? || ' 食品 乳制品'),?)
                """, itemId, householdId, name, barcode, barcode, name, archived);
    }

    private UUID createCategory(String name, UUID parentId) throws Exception {
        String parent = parentId == null ? "null" : "\"" + parentId + "\"";
        MvcResult result = mockMvc.perform(post("/api/v1/categories").with(csrf()).cookie(cookie())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","parentId":%s,"defaultInventoryType":"BATCH","attributeSchema":[]}
                                """.formatted(name, parent)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }
    private jakarta.servlet.http.Cookie cookie() { return new jakarta.servlet.http.Cookie("STOCKET_SESSION", token); }
}
