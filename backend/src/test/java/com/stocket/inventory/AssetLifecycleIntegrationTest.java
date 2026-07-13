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
class AssetLifecycleIntegrationTest extends InventoryCommandTestSupport {

    @Test
    void lostRetiredAndReturnedAssetKeepsQuantityAndStatusAligned() throws Exception {
        UUID entryId = insertAsset();

        var lost = command("/api/v1/inventory/entries/" + entryId + "/lost", "asset-lost",
                "{\"reason\":\"遗失\"}");
        assertThat(lost.getResponse().getStatus()).isEqualTo(200);
        assertThat(lost.getResponse().getContentAsString())
                .contains("\"quantity\":\"0\"").contains("\"assetStatus\":\"LOST\"");

        var returned = command("/api/v1/inventory/entries/" + entryId + "/return", "asset-return",
                "{\"reason\":\"找回\"}");
        assertThat(returned.getResponse().getStatus()).isEqualTo(200);
        assertThat(returned.getResponse().getContentAsString())
                .contains("\"quantity\":\"1\"").contains("\"assetStatus\":\"AVAILABLE\"");

        var retired = command("/api/v1/inventory/entries/" + entryId + "/retire", "asset-retire",
                "{\"reason\":\"报废\"}");
        assertThat(retired.getResponse().getStatus()).isEqualTo(200);
        assertThat(retired.getResponse().getContentAsString())
                .contains("\"quantity\":\"0\"").contains("\"assetStatus\":\"RETIRED\"");
        assertThat(jdbc.queryForObject("select available_quantity from inventory_entry where id=?",
                String.class, entryId)).isEqualTo("0.0000");
        assertThat(jdbc.queryForObject("select status from asset_detail where inventory_entry_id=?",
                String.class, entryId)).isEqualTo("RETIRED");
    }
}
