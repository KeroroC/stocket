package com.stocket.location;

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

import com.jayway.jsonpath.JsonPath;
import com.stocket.identity.internal.authentication.SecureValueGenerator;
import com.stocket.identity.internal.authentication.TokenHasher;

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
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class LocationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired DataSource dataSource;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired SecureValueGenerator secureValueGenerator;
    @Autowired TokenHasher tokenHasher;
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("truncate user_account, household cascade");
    }

    @Test
    void createsTreeWithFullPathsAndResolvesStableCode() throws Exception {
        String admin = createUser("ADMIN");
        LocationResult home = createLocation(admin, "家", null);
        LocationResult kitchen = createLocation(admin, "厨房", home.id());
        LocationResult fridge = createLocation(admin, "冰箱", kitchen.id());

        mockMvc.perform(get("/api/v1/locations").cookie(cookie(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[2].fullPath").value("家 > 厨房 > 冰箱"));

        mockMvc.perform(post("/api/v1/locations/resolve-code")
                        .with(csrf()).cookie(cookie(admin)).contentType(APPLICATION_JSON)
                        .content("""
                                {"payload":"stocket:location:%s"}
                                """.formatted(fridge.publicCode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fridge.id().toString()));
    }

    @Test
    void rejectsCyclesDuplicatesAndMemberWrites() throws Exception {
        String admin = createUser("ADMIN");
        LocationResult home = createLocation(admin, "家", null);
        LocationResult kitchen = createLocation(admin, "厨房", home.id());

        mockMvc.perform(post("/api/v1/locations")
                        .with(csrf()).cookie(cookie(admin)).contentType(APPLICATION_JSON)
                        .content("""
                                {"name":" 家 "}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOCATION_NAME_CONFLICT"));

        mockMvc.perform(patch("/api/v1/locations/{id}", home.id())
                        .with(csrf()).cookie(cookie(admin)).contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"家","parentId":"%s","version":0}
                                """.formatted(kitchen.id())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOCATION_CYCLE"));

        String member = createUser("MEMBER");
        mockMvc.perform(post("/api/v1/locations")
                        .with(csrf()).cookie(cookie(member)).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"无权限\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void allRolesResolveCodesButUnknownCodeIsNotFound() throws Exception {
        String admin = createUser("ADMIN");
        LocationResult home = createLocation(admin, "家", null);
        for (String role : new String[]{"MEMBER", "VIEWER"}) {
            String token = createUser(role);
            mockMvc.perform(post("/api/v1/locations/resolve-code")
                            .with(csrf()).cookie(cookie(token)).contentType(APPLICATION_JSON)
                            .content("{\"payload\":\"stocket:location:%s\"}".formatted(home.publicCode())))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/locations/resolve-code")
                        .with(csrf()).cookie(cookie(admin)).contentType(APPLICATION_JSON)
                        .content("{\"payload\":\"stocket:location:unknown\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOCATION_CODE_NOT_FOUND"));
    }

    private LocationResult createLocation(String token, String name, UUID parentId) throws Exception {
        String parent = parentId == null ? "null" : "\"" + parentId + "\"";
        MvcResult result = mockMvc.perform(post("/api/v1/locations")
                        .with(csrf()).cookie(cookie(token)).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"parentId\":%s}".formatted(name, parent)))
                .andExpect(status().isCreated()).andReturn();
        String body = result.getResponse().getContentAsString();
        return new LocationResult(UUID.fromString(JsonPath.read(body, "$.id")), JsonPath.read(body, "$.publicCode"));
    }

    private String createUserForHousehold(String role, UUID householdId) {
        UUID accountId = UUID.randomUUID();
        String username = role.toLowerCase() + accountId.toString().substring(0, 8);
        jdbc.update("""
                insert into user_account(id, username, normalized_username, display_name, password_hash,
                    status, must_change_password, credentials_changed_at, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, 'ACTIVE', false, now(), now(), now(), 0)
                """, accountId, username, username, username, passwordEncoder.encode("member-password"));
        jdbc.update("""
                insert into household_member(id, household_id, account_id, role, created_at, updated_at)
                values (?, ?, ?, ?, now(), now())
                """, UUID.randomUUID(), householdId, accountId, role);
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

    private String createUser(String role, boolean first) {
        UUID householdId = UUID.randomUUID();
        jdbc.update("insert into household(id, singleton_key, name, timezone) values (?, 1, '家', 'Asia/Shanghai')",
                householdId);
        return createUserForHousehold(role, householdId);
    }

    private String createUser(String role) {
        Integer count = jdbc.queryForObject("select count(*) from household", Integer.class);
        return count == 0 ? createUser(role, true)
                : createUserForHousehold(role, jdbc.queryForObject("select id from household", UUID.class));
    }

    private jakarta.servlet.http.Cookie cookie(String token) {
        return new jakarta.servlet.http.Cookie("STOCKET_SESSION", token);
    }

    private record LocationResult(UUID id, String publicCode) { }
}
