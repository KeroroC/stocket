package com.stocket.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InventoryLedgerAcceptanceTest extends InventoryCommandTestSupport {

    @Test
    void completesInventoryLedgerJourneyWithConcurrencyIdempotencyAndReconciliation() throws Exception {
        jdbc.execute("truncate item_definition, category, location cascade");
        String admin = createSession("ledger-admin", "ADMIN");

        UUID categoryId = id(mockMvc.perform(post("/api/v1/categories").with(csrf()).cookie(cookie(admin))
                .contentType(APPLICATION_JSON).content("""
                        {"name":"家庭物资","defaultInventoryType":"BATCH","attributeSchema":[]}
                        """)).andReturn());
        UUID fridge = createLocation(admin, "冰箱", null);
        UUID pantry = createLocation(admin, "储物间", null);
        itemId = id(mockMvc.perform(post("/api/v1/items").with(csrf()).cookie(cookie(session))
                .contentType(APPLICATION_JSON).content("""
                        {"name":"牛奶","categoryId":"%s","defaultUnit":"盒",
                         "customAttributes":{},"barcodes":[],"tags":["冷藏"]}
                        """.formatted(categoryId))).andReturn());

        MvcResult concurrentBatch = receive("accept-receive-concurrent", "5", fridge, "BATCH", null);
        UUID concurrentEntry = id(concurrentBatch);
        MvcResult replay = receive("accept-receive-concurrent", "5", fridge, "BATCH", null);
        assertThat(replay.getResponse().getStatus()).isEqualTo(201);
        assertThat(replay.getResponse().getContentAsString())
                .isEqualTo(concurrentBatch.getResponse().getContentAsString());

        List<MvcResult> consumption = consumeConcurrently(concurrentEntry, 10);
        assertThat(consumption.stream().filter(result -> result.getResponse().getStatus() == 200)).hasSize(5);
        assertThat(consumption.stream().filter(result -> result.getResponse().getStatus() == 409)).hasSize(5);

        UUID transferSource = id(receive("accept-receive-transfer", "4", fridge, "BATCH", null));
        MvcResult transfer = command("/api/v1/inventory/entries/" + transferSource + "/transfer",
                "accept-transfer", "{\"targetLocationId\":\"%s\",\"quantity\":\"1.5\"}".formatted(pantry));
        assertThat(transfer.getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select count(*) from batch_detail where source_entry_id=?",
                Integer.class, transferSource)).isEqualTo(1);

        UUID asset = id(receive("accept-receive-asset", "1", pantry, "ASSET", "HOME-001"));
        assertThat(command("/api/v1/inventory/entries/" + asset + "/retire", "accept-retire",
                "{\"reason\":\"达到使用寿命\"}").getResponse().getStatus()).isEqualTo(200);

        MvcResult entries = mockMvc.perform(get("/api/v1/inventory/entries").cookie(cookie(session))).andReturn();
        assertThat(entries.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<Integer>read(entries.getResponse().getContentAsString(), "$.total")).isEqualTo(4);
        assertThat(mockMvc.perform(get("/api/v1/inventory/entries/{id}/movements", transferSource)
                .cookie(cookie(session))).andReturn().getResponse().getStatus()).isEqualTo(200);

        assertThat(mockMvc.perform(post("/api/v1/admin/inventory/reconcile").with(csrf())
                .cookie(cookie(admin))).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select count(*) from inventory_reconciliation_issue where status='OPEN'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from (
                    select e.id from inventory_entry e
                    left join inventory_movement m on m.entry_id=e.id
                    group by e.id,e.available_quantity
                    having e.available_quantity <> coalesce(sum(m.quantity_delta),0)
                ) inconsistent
                """, Integer.class)).isZero();
    }

    private UUID createLocation(String token, String name, UUID parentId) throws Exception {
        String parent = parentId == null ? "null" : "\"" + parentId + "\"";
        return id(mockMvc.perform(post("/api/v1/locations").with(csrf()).cookie(cookie(token))
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"parentId\":%s}".formatted(name, parent))).andReturn());
    }

    private MvcResult receive(String key, String quantity, UUID location, String type, String assetNumber)
            throws Exception {
        String asset = assetNumber == null ? "null" : "\"" + assetNumber + "\"";
        return command("/api/v1/inventory/receipts", key, """
                {"itemId":"%s","type":"%s","quantity":"%s","locationId":"%s",
                 "receivedAt":"2026-07-14T00:00:00Z","productionDate":"2026-07-01",
                 "expirationDate":"2026-07-31","batchNumber":"B-ACCEPT","assetNumber":%s,
                 "customAttributes":{}}
                """.formatted(itemId, type, quantity, location, asset));
    }

    private List<MvcResult> consumeConcurrently(UUID entryId, int requests) throws Exception {
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(requests)) {
            for (int index = 0; index < requests; index++) {
                int request = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return command("/api/v1/inventory/entries/" + entryId + "/consume",
                            "accept-consume-" + request, "{\"quantity\":\"1\"}");
                }));
            }
            ready.await();
            start.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) results.add(future.get());
            return results;
        }
    }

    private UUID id(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isBetween(200, 299);
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }
}
