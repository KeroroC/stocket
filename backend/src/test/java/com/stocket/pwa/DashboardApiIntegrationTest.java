package com.stocket.pwa;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.stocket.identity.internal.authentication.SecureValueGenerator;
import com.stocket.identity.internal.authentication.TokenHasher;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DashboardApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired DataSource dataSource;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired SecureValueGenerator values;
    @Autowired TokenHasher hasher;

    JdbcTemplate jdbc;
    UUID householdId;
    String session;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("truncate user_account, household cascade");
        householdId = UUID.randomUUID();
        jdbc.update("insert into household(id,singleton_key,name,timezone) values (?,1,'王家','Asia/Shanghai')",
                householdId);
        session = createSession();
    }

    @Test
    void returnsHouseholdScopedTaskSummaryAndInventoryEnrichedSearch() throws Exception {
        UUID categoryId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        jdbc.update("""
                insert into category(id,household_id,name,normalized_name,default_inventory_type,attribute_schema)
                values (?,?,'食品','食品','BATCH','[]'::jsonb)
                """, categoryId, householdId);
        jdbc.update("""
                insert into location(id,household_id,name,normalized_name,public_code)
                values (?,?,'冰箱','冰箱','FRIDGE')
                """, locationId, householdId);
        jdbc.update("""
                insert into item_definition(id,household_id,category_id,name,normalized_name,default_unit,custom_attributes)
                values (?,?,?,'鲜牛奶','鲜牛奶','盒','{}'::jsonb)
                """, itemId, householdId, categoryId);
        jdbc.update("""
                insert into item_barcode(id,household_id,item_definition_id,raw_value,normalized_value)
                values (?,?,?,'690001','690001')
                """, UUID.randomUUID(), householdId, itemId);
        jdbc.update("""
                insert into inventory_entry(id,household_id,item_definition_id,location_id,inventory_type,
                    available_quantity,received_at,expiration_date,custom_attributes)
                values (?,?,?,?,'BATCH',3,now(),?,'{}'::jsonb)
                """, entryId, householdId, itemId, locationId, LocalDate.now().plusDays(5));
        jdbc.update("insert into batch_detail(inventory_entry_id,batch_number) values (?,'B-01')", entryId);
        jdbc.update("""
                insert into reminder(id,household_id,item_definition_id,inventory_entry_id,reminder_type,
                    trigger_key,trigger_at,status,opened_at,created_at,updated_at)
                values (?,?,?,?,'EXPIRING','EXPIRING:5',now(),'OPEN',now(),now(),now())
                """, UUID.randomUUID(), householdId, itemId, entryId);

        mockMvc.perform(get("/api/v1/dashboard").cookie(cookie(session)).param("q", "690001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.expiring").value(1))
                .andExpect(jsonPath("$.summary.openTotal").value(1))
                .andExpect(jsonPath("$.search[0].id").value(itemId.toString()))
                .andExpect(jsonPath("$.search[0].matchType").value("BARCODE_EXACT"))
                .andExpect(jsonPath("$.search[0].totalAvailable").value("3"))
                .andExpect(jsonPath("$.search[0].locations[0]").value("冰箱"))
                .andExpect(jsonPath("$.search[0].recentBatch").value("B-01"))
                .andExpect(jsonPath("$.search.length()").value(1));
    }

    private String createSession() {
        UUID accountId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        jdbc.update("""
                insert into user_account(id,username,normalized_username,display_name,password_hash,status,
                    must_change_password,credentials_changed_at,created_at,updated_at,version)
                values (?,?,?,?,?,'ACTIVE',false,now(),now(),now(),0)
                """, accountId, "dashboard-member", "dashboard-member", "成员",
                passwordEncoder.encode("correct horse battery staple"));
        jdbc.update("""
                insert into household_member(id,household_id,account_id,role,created_at,updated_at)
                values (?,?,?,'MEMBER',now(),now())
                """, memberId, householdId, accountId);
        String token = values.generateToken();
        Instant now = Instant.now();
        jdbc.update("""
                insert into user_session(id,account_id,token_hash,created_at,last_seen_at,idle_expires_at,
                    absolute_expires_at)
                values (?,?,?,?::timestamptz,?::timestamptz,?::timestamptz,?::timestamptz)
                """, UUID.randomUUID(), accountId, hasher.sha256(token), now.toString(), now.toString(),
                now.plus(30, ChronoUnit.DAYS).toString(), now.plus(90, ChronoUnit.DAYS).toString());
        return token;
    }

    private jakarta.servlet.http.Cookie cookie(String token) {
        return new jakarta.servlet.http.Cookie("STOCKET_SESSION", token);
    }
}
