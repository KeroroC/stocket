package com.stocket.system.export;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DisabledInAotMode
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class CsvExportIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");
    @Autowired MockMvc mockMvc;
    @Autowired DataSource dataSource;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired SecureValueGenerator values;
    @Autowired TokenHasher hasher;
    JdbcTemplate jdbc;
    UUID householdId;
    UUID itemId;
    UUID locationId;
    String token;

    @BeforeEach void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("truncate user_account, household cascade");
        householdId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        locationId = UUID.randomUUID();
        jdbc.update("insert into household(id,singleton_key,name,timezone) values (?,1,'家','Asia/Shanghai')", householdId);
        jdbc.update("insert into user_account(id,username,normalized_username,display_name,password_hash,status,must_change_password,credentials_changed_at,created_at,updated_at,version) values (?,'viewer','viewer','查看者',?,'ACTIVE',false,now(),now(),now(),0)", accountId, passwordEncoder.encode("correct horse battery staple"));
        jdbc.update("insert into household_member(id,household_id,account_id,role,created_at,updated_at) values (?,?,?,'VIEWER',now(),now())", UUID.randomUUID(), householdId, accountId);
        token = values.generateToken(); Instant now = Instant.now();
        jdbc.update("insert into user_session(id,account_id,token_hash,created_at,last_seen_at,idle_expires_at,absolute_expires_at) values (?,?,?,?::timestamptz,?::timestamptz,?::timestamptz,?::timestamptz)", UUID.randomUUID(), accountId, hasher.sha256(token), now.toString(), now.toString(), now.plus(30, ChronoUnit.DAYS).toString(), now.plus(90, ChronoUnit.DAYS).toString());
        jdbc.update("insert into item_definition(id,household_id,name,normalized_name,default_unit,custom_attributes) values (?,?, '导出牛奶','导出牛奶','盒','{}'::jsonb)", itemId, householdId);
        jdbc.update("insert into catalog_search_projection(item_definition_id,household_id,display_name,category_path,tags,raw_barcodes,normalized_barcodes,searchable_text,archived) values (?,?, '导出牛奶','食品',array['早餐'],array['6901'],array['6901'],'导出牛奶 食品 早餐',false)", itemId, householdId);
        jdbc.update("insert into location(id,household_id,name,normalized_name,public_code) values (?,?, '冰箱','冰箱','LOC-1')", locationId, householdId);
        insertEntry("5"); insertEntry("2");
    }

    @Test void exportsTheSameFilteredIdsWithUtf8BomAndPrivateDownloadHeaders() throws Exception {
        MvcResult search = mockMvc.perform(get("/api/v1/catalog/search").queryParam("q", "牛奶").cookie(cookie())).andReturn();
        List<String> catalogIds = JsonPath.read(search.getResponse().getContentAsString(), "$.items[*].id");
        String catalogCsv = csv("/api/v1/exports/catalog.csv", "q", "牛奶");
        assertThat(catalogCsv.charAt(0)).isEqualTo('\ufeff');
        assertThat(catalogCsv).contains("id,name,categoryPath,brand,model,specification,tags,barcodes")
                .contains(itemId.toString()).contains("导出牛奶");
        assertThat(catalogIds).containsExactly(itemId.toString());

        MvcResult entries = mockMvc.perform(get("/api/v1/inventory/entries").queryParam("type", "BATCH").cookie(cookie())).andReturn();
        List<String> inventoryIds = JsonPath.read(entries.getResponse().getContentAsString(), "$.items[*].id");
        String inventoryCsv = csv("/api/v1/exports/inventory.csv", "type", "BATCH");
        assertThat(inventoryCsv.charAt(0)).isEqualTo('\ufeff');
        for (String id : inventoryIds) assertThat(inventoryCsv).contains(id);
        assertThat(inventoryCsv.lines().filter(line -> line.matches("[0-9a-f-]{36},.*")).count()).isEqualTo(2);
    }

    private String csv(String path, String parameter, String value) throws Exception {
        MvcResult pending = mockMvc.perform(get(path).queryParam(parameter, value).cookie(cookie()))
                .andExpect(request().asyncStarted()).andReturn();
        MvcResult result = mockMvc.perform(asyncDispatch(pending)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store"))).andReturn();
        return result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private void insertEntry(String quantity) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into inventory_entry(id,household_id,item_definition_id,location_id,inventory_type,available_quantity,received_at,custom_attributes) values (?,?,?,?, 'BATCH',?::numeric,now(),'{}'::jsonb)", id, householdId, itemId, locationId, quantity);
        jdbc.update("insert into batch_detail(inventory_entry_id,batch_number) values (?,'B-1')", id);
    }
    private jakarta.servlet.http.Cookie cookie() { return new jakarta.servlet.http.Cookie("STOCKET_SESSION", token); }
}
