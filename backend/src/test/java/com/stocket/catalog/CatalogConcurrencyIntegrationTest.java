package com.stocket.catalog;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@DisabledInAotMode
class CatalogConcurrencyIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired DataSource dataSource;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired SecureValueGenerator values;
    @Autowired TokenHasher hasher;

    private JdbcTemplate jdbc;
    private String token;
    private UUID categoryId;

    @BeforeEach
    void setup() throws Exception {
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
        MvcResult category = mockMvc.perform(post("/api/v1/categories").with(csrf()).cookie(cookie())
                        .contentType(APPLICATION_JSON).content("""
                                {"name":"食品","defaultInventoryType":"BATCH","attributeSchema":[]}
                                """))
                .andExpect(status().isCreated()).andReturn();
        categoryId = UUID.fromString(JsonPath.read(category.getResponse().getContentAsString(), "$.id"));
    }

    @Test
    void sameVersionUpdatesProduceOneSuccessAndOneConflict() throws Exception {
        UUID itemId = UUID.fromString(JsonPath.read(createItem("initial", "seed").getResponse().getContentAsString(), "$.id"));
        List<Integer> statuses = race(
                () -> updateItem(itemId, "first", "seed", 0),
                () -> updateItem(itemId, "second", "seed", 0));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    }

    @Test
    void sameBarcodeCreatesProduceOneSuccessWithoutOrphanItem() throws Exception {
        List<Integer> statuses = race(
                () -> createItem("first", " same-code ").getResponse().getStatus(),
                () -> createItem("second", "SAME-CODE").getResponse().getStatus());

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("select count(*) from item_definition", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from item_barcode", Integer.class)).isEqualTo(1);
    }

    private List<Integer> race(ThrowingIntSupplier first, ThrowingIntSupplier second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Integer> firstResult = executor.submit(() -> awaitAndRun(ready, start, first));
            Future<Integer> secondResult = executor.submit(() -> awaitAndRun(ready, start, second));
            ready.await();
            start.countDown();
            return List.of(firstResult.get(), secondResult.get());
        }
    }

    private int awaitAndRun(CountDownLatch ready, CountDownLatch start, ThrowingIntSupplier action) throws Exception {
        ready.countDown();
        start.await();
        return action.getAsInt();
    }

    private MvcResult createItem(String name, String barcode) throws Exception {
        return mockMvc.perform(post("/api/v1/items").with(csrf()).cookie(cookie()).contentType(APPLICATION_JSON)
                        .content(itemJson(name, barcode, null)))
                .andReturn();
    }

    private int updateItem(UUID itemId, String name, String barcode, long version) throws Exception {
        return mockMvc.perform(patch("/api/v1/items/{id}", itemId).with(csrf()).cookie(cookie())
                        .contentType(APPLICATION_JSON).content(itemJson(name, barcode, version)))
                .andReturn().getResponse().getStatus();
    }

    private String itemJson(String name, String barcode, Long version) {
        String versionField = version == null ? "" : ",\"version\":" + version;
        return """
                {"name":"%s","categoryId":"%s","defaultUnit":"盒","customAttributes":{},
                 "barcodes":["%s"],"tags":[]%s}
                """.formatted(name, categoryId, barcode, versionField);
    }

    private jakarta.servlet.http.Cookie cookie() {
        return new jakarta.servlet.http.Cookie("STOCKET_SESSION", token);
    }

    @FunctionalInterface
    private interface ThrowingIntSupplier {
        int getAsInt() throws Exception;
    }
}
