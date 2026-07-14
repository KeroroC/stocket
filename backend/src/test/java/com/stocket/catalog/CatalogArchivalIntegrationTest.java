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
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@DisabledInAotMode
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class CatalogArchivalIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired DataSource dataSource;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired SecureValueGenerator values;
    @Autowired TokenHasher hasher;

    private JdbcTemplate jdbc;
    private String token;

    @BeforeEach
    void setup() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("truncate user_account, household cascade");
        UUID householdId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        jdbc.update("insert into household(id,singleton_key,name,timezone) values (?,1,'家','Asia/Shanghai')", householdId);
        jdbc.update("""
                insert into user_account(id,username,normalized_username,display_name,password_hash,status,
                    must_change_password,credentials_changed_at,created_at,updated_at,version)
                values (?,'owner','owner','主人',?,'ACTIVE',false,now(),now(),now(),0)
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
    }

    @Test
    void categoryWithActiveChildCannotBeArchivedAndChildNeedsActiveParentToRestore() throws Exception {
        UUID parentId = createCategory("食品", null);
        UUID childId = createCategory("乳制品", parentId);

        archiveCategory(parentId, 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_EMPTY"));

        archiveCategory(childId, 0).andExpect(status().isOk());
        archiveCategory(parentId, 0).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/categories/{id}/restore", childId).queryParam("version", "1")
                        .with(csrf()).cookie(cookie()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_PARENT_ARCHIVED"));
    }

    @Test
    void locationWithActiveChildCannotBeArchivedAndChildNeedsActiveParentToRestore() throws Exception {
        UUID parentId = createLocation("家", null);
        UUID childId = createLocation("厨房", parentId);

        archiveLocation(parentId, 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOCATION_NOT_EMPTY"));

        archiveLocation(childId, 0).andExpect(status().isOk());
        archiveLocation(parentId, 0).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/locations/{id}/restore", childId).queryParam("version", "1")
                        .with(csrf()).cookie(cookie()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOCATION_PARENT_ARCHIVED"));
    }

    private UUID createCategory(String name, UUID parentId) throws Exception {
        String parent = parentId == null ? "null" : "\"" + parentId + "\"";
        MvcResult result = mockMvc.perform(post("/api/v1/categories").with(csrf()).cookie(cookie())
                        .contentType(APPLICATION_JSON).content("""
                                {"name":"%s","parentId":%s,"defaultInventoryType":"BATCH","attributeSchema":[]}
                                """.formatted(name, parent)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private UUID createLocation(String name, UUID parentId) throws Exception {
        String parent = parentId == null ? "null" : "\"" + parentId + "\"";
        MvcResult result = mockMvc.perform(post("/api/v1/locations").with(csrf()).cookie(cookie())
                        .contentType(APPLICATION_JSON).content("""
                                {"name":"%s","parentId":%s}
                                """.formatted(name, parent)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private org.springframework.test.web.servlet.ResultActions archiveCategory(UUID id, long version) throws Exception {
        return mockMvc.perform(post("/api/v1/categories/{id}/archive", id).queryParam("version", Long.toString(version))
                .with(csrf()).cookie(cookie()));
    }

    private org.springframework.test.web.servlet.ResultActions archiveLocation(UUID id, long version) throws Exception {
        return mockMvc.perform(post("/api/v1/locations/{id}/archive", id).queryParam("version", Long.toString(version))
                .with(csrf()).cookie(cookie()));
    }

    private jakarta.servlet.http.Cookie cookie() {
        return new jakarta.servlet.http.Cookie("STOCKET_SESSION", token);
    }
}
