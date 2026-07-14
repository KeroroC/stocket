package com.stocket.attachment;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.sql.DataSource;

import com.jayway.jsonpath.JsonPath;
import com.stocket.identity.internal.authentication.SecureValueGenerator;
import com.stocket.identity.internal.authentication.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class AttachmentExportAuditAcceptanceTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");
    static final Path attachmentDir = temp();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("stocket.attachment.directory", () -> attachmentDir.toString()); }
    @Autowired MockMvc mockMvc; @Autowired DataSource dataSource; @Autowired PasswordEncoder passwordEncoder;
    @Autowired SecureValueGenerator values; @Autowired TokenHasher hasher;
    JdbcTemplate jdbc; UUID householdId; UUID itemId; UUID locationId; String member; String viewer; String admin;

    @BeforeEach void setUp() {
        jdbc=new JdbcTemplate(dataSource);jdbc.execute("truncate user_account, household cascade");
        householdId=UUID.randomUUID();itemId=UUID.randomUUID();locationId=UUID.randomUUID();
        jdbc.update("insert into household(id,singleton_key,name,timezone) values (?,1,'家','Asia/Shanghai')",householdId);
        jdbc.update("insert into item_definition(id,household_id,name,normalized_name,default_unit,custom_attributes) values (?,?, '验收牛奶','验收牛奶','盒','{}'::jsonb)",itemId,householdId);
        jdbc.update("insert into catalog_search_projection(item_definition_id,household_id,display_name,category_path,tags,raw_barcodes,normalized_barcodes,searchable_text,archived) values (?,?, '验收牛奶','食品',array[]::varchar[],array[]::varchar[],array[]::varchar[],'验收牛奶 食品',false)",itemId,householdId);
        jdbc.update("insert into location(id,household_id,name,normalized_name,public_code) values (?,?, '冰箱','冰箱','ACC-LOC')",locationId,householdId);
        member=session("accept-member","MEMBER");viewer=session("accept-viewer","VIEWER");admin=session("accept-admin","ADMIN");
    }

    @Test void securesAttachmentsAndCorrelatesInventoryExportAndAudit() throws Exception {
        UUID image=upload(member,"COVER_IMAGE",image());
        upload(member,"INVOICE",new MockMultipartFile("file","invoice.pdf","application/pdf","%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes()));
        MvcResult pending=mockMvc.perform(get("/api/v1/attachments/{id}/content",image).cookie(cookie(viewer)))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(pending)).andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/attachments").file(image()).with(csrf()).cookie(cookie(viewer))
                .param("ownerType","ITEM_DEFINITION").param("ownerId",itemId.toString()).param("purpose","ITEM_IMAGE"))
                .andExpect(status().isForbidden());

        MvcResult exportPending=mockMvc.perform(get("/api/v1/exports/catalog.csv").param("q","验收牛奶").cookie(cookie(viewer)))
                .andExpect(request().asyncStarted()).andReturn();
        String csv=mockMvc.perform(asyncDispatch(exportPending)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(csv).contains(itemId.toString()).contains("验收牛奶");

        MvcResult received=mockMvc.perform(post("/api/v1/inventory/receipts").with(csrf()).cookie(cookie(member))
                .header("Idempotency-Key","accept-receive").header("X-Request-Id","acceptance-request-123")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"itemId":"%s","type":"BATCH","quantity":"2","locationId":"%s",
                         "receivedAt":"2026-07-14T00:00:00Z","batchNumber":"ACC-1","customAttributes":{}}
                        """.formatted(itemId,locationId))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").value("acceptance-request-123")).andReturn();
        UUID entryId=UUID.fromString(JsonPath.read(received.getResponse().getContentAsString(),"$.id"));
        mockMvc.perform(get("/api/v1/admin/audit-logs").param("requestId","acceptance-request-123").cookie(cookie(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].eventType").value("InventoryReceived"));
        assertThat(jdbc.queryForObject("select request_id from inventory_movement where entry_id=?",String.class,entryId)).isEqualTo("acceptance-request-123");
        assertThat(jdbc.queryForObject("select count(*) from attachment where owner_id=? and status='AVAILABLE'",Integer.class,itemId)).isEqualTo(2);

        mockMvc.perform(post("/api/v1/categories").with(csrf()).cookie(cookie(admin))
                .header("X-Request-Id","acceptance-catalog-123").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"饮品\",\"defaultInventoryType\":\"BATCH\",\"attributeSchema\":[]}"))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select count(*) from audit_log where event_type='CatalogChanged' and request_id='acceptance-catalog-123'",Integer.class))
                .isEqualTo(1);
    }

    private UUID upload(String token,String purpose,MockMultipartFile file)throws Exception{MvcResult result=mockMvc.perform(multipart("/api/v1/attachments").file(file).with(csrf()).cookie(cookie(token)).header("X-Request-Id","acceptance-request-123").param("ownerType","ITEM_DEFINITION").param("ownerId",itemId.toString()).param("purpose",purpose)).andExpect(status().isCreated()).andReturn();return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(),"$.id"));}
    private MockMultipartFile image()throws Exception{BufferedImage image=new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB);ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return new MockMultipartFile("file","cover.png","image/png",out.toByteArray());}
    private String session(String username,String role){UUID account=UUID.randomUUID();jdbc.update("insert into user_account(id,username,normalized_username,display_name,password_hash,status,must_change_password,credentials_changed_at,created_at,updated_at,version) values (?,?,?,?,?,'ACTIVE',false,now(),now(),now(),0)",account,username,username,username,passwordEncoder.encode("correct horse battery staple"));jdbc.update("insert into household_member(id,household_id,account_id,role,created_at,updated_at) values (?,?,?,?,now(),now())",UUID.randomUUID(),householdId,account,role);String token=values.generateToken();Instant now=Instant.now();jdbc.update("insert into user_session(id,account_id,token_hash,created_at,last_seen_at,idle_expires_at,absolute_expires_at) values (?,?,?,?::timestamptz,?::timestamptz,?::timestamptz,?::timestamptz)",UUID.randomUUID(),account,hasher.sha256(token),now.toString(),now.toString(),now.plus(30,ChronoUnit.DAYS).toString(),now.plus(90,ChronoUnit.DAYS).toString());return token;}
    private jakarta.servlet.http.Cookie cookie(String token){return new jakarta.servlet.http.Cookie("STOCKET_SESSION",token);}private static Path temp(){try{return Files.createTempDirectory("stocket-stage7-acceptance-");}catch(Exception error){throw new RuntimeException(error);}}
}
