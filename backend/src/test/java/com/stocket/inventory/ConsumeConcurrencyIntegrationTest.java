package com.stocket.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@DisabledInAotMode
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConsumeConcurrencyIntegrationTest extends InventoryCommandTestSupport {

    @Test
    void concurrentConsumptionNeverCreatesNegativeStock() throws Exception {
        UUID entryId = insertBatch("10");
        int requests = 20;
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
                            "consume-" + request, "{\"quantity\":\"1\"}");
                }));
            }
            ready.await();
            start.countDown();

            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get());
            }
            assertThat(results.stream().filter(result -> result.getResponse().getStatus() == 200)).hasSize(10);
            assertThat(results.stream().filter(result -> result.getResponse().getStatus() == 409)).hasSize(10);
            assertThat(results.stream().filter(result -> result.getResponse().getStatus() == 409))
                    .allSatisfy(result -> assertThat(result.getResponse().getContentAsString())
                            .contains("INSUFFICIENT_STOCK"));
        }

        assertThat(jdbc.queryForObject("select available_quantity from inventory_entry where id=?",
                String.class, entryId)).isEqualTo("0.0000");
        assertThat(jdbc.queryForObject("select sum(quantity_delta) from inventory_movement where entry_id=?",
                String.class, entryId)).isEqualTo("0.0000");
    }
}
