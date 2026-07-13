package com.stocket.inventory;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@DisabledInAotMode
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdjustmentIntegrationTest extends InventoryCommandTestSupport {

    @Test
    void negativeAdjustmentRequiresReasonAndRecordsTargetDelta() throws Exception {
        UUID entryId = insertBatch("5");

        var missingReason = command("/api/v1/inventory/entries/" + entryId + "/adjust",
                "adjust-no-reason", "{\"targetQuantity\":\"3\"}");
        assertThat(missingReason.getResponse().getStatus()).isEqualTo(422);
        assertThat(missingReason.getResponse().getContentAsString()).contains("ADJUSTMENT_REASON_REQUIRED");

        var adjusted = command("/api/v1/inventory/entries/" + entryId + "/adjust",
                "adjust-with-reason", "{\"targetQuantity\":\"3\",\"reason\":\"盘点修正\"}");
        assertThat(adjusted.getResponse().getStatus()).isEqualTo(200);
        assertThat(adjusted.getResponse().getContentAsString()).contains("\"quantity\":\"3\"");
        assertThat(jdbc.queryForObject("select sum(quantity_delta) from inventory_movement where entry_id=?",
                String.class, entryId)).isEqualTo("3.0000");
    }

    @Test
    void returnCannotExceedHistoricalConsumption() throws Exception {
        UUID entryId = insertBatch("5");
        assertThat(command("/api/v1/inventory/entries/" + entryId + "/consume", "consume-two",
                "{\"quantity\":\"2\"}").getResponse().getStatus()).isEqualTo(200);

        var excessive = command("/api/v1/inventory/entries/" + entryId + "/return", "return-three",
                "{\"quantity\":\"3\",\"reason\":\"退回\"}");
        assertThat(excessive.getResponse().getStatus()).isEqualTo(409);
        assertThat(excessive.getResponse().getContentAsString()).contains("RETURN_EXCEEDS_CONSUMED");

        var returned = command("/api/v1/inventory/entries/" + entryId + "/return", "return-two",
                "{\"quantity\":\"2\",\"reason\":\"退回\"}");
        assertThat(returned.getResponse().getStatus()).isEqualTo(200);
        assertThat(returned.getResponse().getContentAsString()).contains("\"quantity\":\"5\"");
    }

    @Test
    void idempotencyKeyIsBoundToThePathEntry() throws Exception {
        UUID firstEntry = insertBatch("5");
        UUID secondEntry = insertBatch("5");

        assertThat(command("/api/v1/inventory/entries/" + firstEntry + "/adjust", "same-adjust-key",
                "{\"targetQuantity\":\"6\"}").getResponse().getStatus()).isEqualTo(200);

        var reused = command("/api/v1/inventory/entries/" + secondEntry + "/adjust", "same-adjust-key",
                "{\"targetQuantity\":\"6\"}");
        assertThat(reused.getResponse().getStatus()).isEqualTo(409);
        assertThat(reused.getResponse().getContentAsString()).contains("IDEMPOTENCY_KEY_REUSED");
        assertThat(jdbc.queryForObject("select available_quantity from inventory_entry where id=?",
                String.class, secondEntry)).isEqualTo("5.0000");
    }
}
