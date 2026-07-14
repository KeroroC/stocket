package com.stocket.catalog;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.sql.DataSource;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

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
class CatalogLocationAcceptanceTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired DataSource dataSource;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired SecureValueGenerator values;
    @Autowired TokenHasher hasher;

    private JdbcTemplate jdbc;
    private UUID householdId;

    @BeforeEach
    void setup() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("truncate user_account, household cascade");
        householdId = UUID.randomUUID();
        jdbc.update("insert into household(id,singleton_key,name,timezone) values (?,1,'王家','Asia/Shanghai')", householdId);
    }

    @Test
    void completesCatalogAndLocationJourneyAcrossRoles() throws Exception {
        String admin = createSession("admin", "ADMIN");
        String member = createSession("member", "MEMBER");
        String viewer = createSession("viewer", "VIEWER");

        UUID categoryId = responseId(mockMvc.perform(post("/api/v1/categories").with(csrf()).cookie(cookie(admin))
                        .contentType(APPLICATION_JSON).content("""
                                {"name":"乳制品","defaultInventoryType":"BATCH","attributeSchema":[
                                  {"key":"storageTemperature","label":"储存温度","type":"NUMBER",
                                   "required":true,"defaultValue":4,"options":[],"order":0}]}
                                """))
                .andExpect(status().isCreated()).andReturn());

        UUID homeId = responseId(createLocation(admin, "家", null));
        createLocation(admin, "冰箱", homeId).getResponse();

        UUID itemId = responseId(mockMvc.perform(post("/api/v1/items").with(csrf()).cookie(cookie(member))
                        .contentType(APPLICATION_JSON).content("""
                                {"name":"鲜牛奶","categoryId":"%s","brand":"晨光","specification":"250ml",
                                 "defaultUnit":"盒","customAttributes":{"storageTemperature":4},
                                 "barcodes":["6901234567890"],"tags":["早餐","冷藏"]}
                                """.formatted(categoryId)))
                .andExpect(status().isCreated()).andReturn());

        mockMvc.perform(get("/api/v1/catalog/search").queryParam("q", "牛奶").cookie(cookie(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("鲜牛奶"));
        mockMvc.perform(get("/api/v1/catalog/search").queryParam("q", " 6901234567890 ").cookie(cookie(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].matchType").value("BARCODE_EXACT"));
        mockMvc.perform(get("/api/v1/items/{id}", UUID.randomUUID()).cookie(cookie(viewer)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ITEM_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/items/{id}/archive", itemId).queryParam("version", "0")
                        .with(csrf()).cookie(cookie(member)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/catalog/search").queryParam("q", "牛奶").cookie(cookie(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)));
    }

    private MvcResult createLocation(String token, String name, UUID parentId) throws Exception {
        String parent = parentId == null ? "null" : "\"" + parentId + "\"";
        return mockMvc.perform(post("/api/v1/locations").with(csrf()).cookie(cookie(token))
                        .contentType(APPLICATION_JSON).content("""
                                {"name":"%s","parentId":%s}
                                """.formatted(name, parent)))
                .andExpect(status().isCreated()).andReturn();
    }

    private String createSession(String username, String role) {
        UUID accountId = UUID.randomUUID();
        jdbc.update("""
                insert into user_account(id,username,normalized_username,display_name,password_hash,status,
                    must_change_password,credentials_changed_at,created_at,updated_at,version)
                values (?,?,?,?,?,'ACTIVE',false,now(),now(),now(),0)
                """, accountId, username, username, username, passwordEncoder.encode("correct horse battery staple"));
        jdbc.update("insert into household_member(id,household_id,account_id,role,created_at,updated_at) values (?,?,?,?,now(),now())",
                UUID.randomUUID(), householdId, accountId, role);
        String token = values.generateToken();
        Instant now = Instant.now();
        jdbc.update("""
                insert into user_session(id,account_id,token_hash,created_at,last_seen_at,idle_expires_at,absolute_expires_at)
                values (?,?,?,?::timestamptz,?::timestamptz,?::timestamptz,?::timestamptz)
                """, UUID.randomUUID(), accountId, hasher.sha256(token), now.toString(), now.toString(),
                now.plus(30, ChronoUnit.DAYS).toString(), now.plus(90, ChronoUnit.DAYS).toString());
        return token;
    }

    private UUID responseId(MvcResult result) throws Exception {
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private jakarta.servlet.http.Cookie cookie(String token) {
        return new jakarta.servlet.http.Cookie("STOCKET_SESSION", token);
    }
}
