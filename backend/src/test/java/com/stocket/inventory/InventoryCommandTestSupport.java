package com.stocket.inventory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.sql.DataSource;

import com.stocket.identity.internal.authentication.SecureValueGenerator;
import com.stocket.identity.internal.authentication.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

abstract class InventoryCommandTestSupport {

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
    UUID itemId;
    UUID locationId;
    UUID accountId;
    String session;

    @BeforeEach
    void setUpInventoryFixture() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("truncate user_account, household cascade");
        householdId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        locationId = UUID.randomUUID();
        jdbc.update("insert into household(id,singleton_key,name,timezone) values (?,1,'王家','Asia/Shanghai')",
                householdId);
        jdbc.update("""
                insert into item_definition(id,household_id,name,normalized_name,default_unit,custom_attributes)
                values (?,?,'牛奶','牛奶','盒','{}'::jsonb)
                """, itemId, householdId);
        jdbc.update("""
                insert into location(id,household_id,name,normalized_name,public_code)
                values (?,?,'冰箱','冰箱',?)
                """, locationId, householdId, UUID.randomUUID().toString());
        session = createSession("member", "MEMBER");
    }

    UUID insertBatch(String quantity) {
        UUID entryId = UUID.randomUUID();
        jdbc.update("""
                insert into inventory_entry(id,household_id,item_definition_id,location_id,inventory_type,
                    available_quantity,received_at,custom_attributes)
                values (?,?,?,?,'BATCH',?::numeric,now(),'{}'::jsonb)
                """, entryId, householdId, itemId, locationId, quantity);
        jdbc.update("insert into batch_detail(inventory_entry_id,batch_number) values (?, 'B-001')", entryId);
        insertInitialMovement(entryId, quantity);
        return entryId;
    }

    UUID insertAsset() {
        UUID entryId = UUID.randomUUID();
        jdbc.update("""
                insert into inventory_entry(id,household_id,item_definition_id,location_id,inventory_type,
                    available_quantity,received_at,custom_attributes)
                values (?,?,?,?,'ASSET',1,now(),'{}'::jsonb)
                """, entryId, householdId, itemId, locationId);
        jdbc.update("""
                insert into asset_detail(inventory_entry_id,household_id,asset_number,status)
                values (?,?,'ASSET-001','AVAILABLE')
                """, entryId, householdId);
        insertInitialMovement(entryId, "1");
        return entryId;
    }

    MvcResult command(String path, String key, String body) throws Exception {
        return mockMvc.perform(post(path).with(csrf()).cookie(cookie(session))
                        .header("Idempotency-Key", key).contentType(APPLICATION_JSON).content(body))
                .andReturn();
    }

    String createSession(String username, String role) {
        accountId = UUID.randomUUID();
        jdbc.update("""
                insert into user_account(id,username,normalized_username,display_name,password_hash,status,
                    must_change_password,credentials_changed_at,created_at,updated_at,version)
                values (?,?,?,?,?,'ACTIVE',false,now(),now(),now(),0)
                """, accountId, username, username, username,
                passwordEncoder.encode("correct horse battery staple"));
        jdbc.update("""
                insert into household_member(id,household_id,account_id,role,created_at,updated_at)
                values (?,?,?,?,now(),now())
                """, UUID.randomUUID(), householdId, accountId, role);
        String token = values.generateToken();
        Instant now = Instant.now();
        jdbc.update("""
                insert into user_session(id,account_id,token_hash,created_at,last_seen_at,idle_expires_at,absolute_expires_at)
                values (?,?,?,?::timestamptz,?::timestamptz,?::timestamptz,?::timestamptz)
                """, UUID.randomUUID(), accountId, hasher.sha256(token), now.toString(), now.toString(),
                now.plus(30, ChronoUnit.DAYS).toString(), now.plus(90, ChronoUnit.DAYS).toString());
        return token;
    }

    private void insertInitialMovement(UUID entryId, String quantity) {
        UUID idempotencyId = UUID.randomUUID();
        jdbc.update("""
                insert into idempotency_record(id,household_id,account_id,operation,idempotency_key,request_hash,
                    status,http_status,response_body,created_at,expires_at)
                values (?,?,?,'RECEIVE',?,repeat('a',64),'COMPLETED',201,'{}'::jsonb,now(),now()+interval '30 days')
                """, idempotencyId, householdId, accountId, UUID.randomUUID().toString());
        jdbc.update("""
                insert into inventory_movement(id,household_id,entry_id,movement_type,quantity_delta,
                    to_location_id,actor_account_id,idempotency_record_id,request_id,occurred_at)
                values (?,?,?,'RECEIVE',?::numeric,?,?,?,?,now())
                """, UUID.randomUUID(), householdId, entryId, quantity, locationId, accountId,
                idempotencyId, UUID.randomUUID().toString());
    }

    jakarta.servlet.http.Cookie cookie(String token) {
        return new jakarta.servlet.http.Cookie("STOCKET_SESSION", token);
    }
}
