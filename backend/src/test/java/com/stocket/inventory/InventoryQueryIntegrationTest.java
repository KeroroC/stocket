package com.stocket.inventory;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@DisabledInAotMode
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InventoryQueryIntegrationTest extends InventoryCommandTestSupport {

    @Test
    void filtersAndPagesEntriesWithStableExpirationOrdering() throws Exception {
        UUID expiring = insertBatch("5");
        jdbc.update("""
                update inventory_entry set expiration_date='2026-07-20', received_at='2026-01-01T00:00:00Z'
                where id=?
                """, expiring);
        UUID noExpiration = insertBatch("3");
        jdbc.update("update inventory_entry set received_at='2026-01-02T00:00:00Z' where id=?", noExpiration);
        UUID asset = insertAsset();
        String viewer = createSession("viewer", "VIEWER");

        MvcResult page = mockMvc.perform(get("/api/v1/inventory/entries")
                        .queryParam("page", "0").queryParam("size", "2").cookie(cookie(viewer)))
                .andReturn();
        assertThat(page.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(page.getResponse().getContentAsString(), "$.items[0].id"))
                .isEqualTo(expiring.toString());
        assertThat(JsonPath.<Integer>read(page.getResponse().getContentAsString(), "$.total")).isEqualTo(3);

        MvcResult batchFilter = mockMvc.perform(get("/api/v1/inventory/entries")
                        .queryParam("type", "BATCH").queryParam("locationId", locationId.toString())
                        .queryParam("expiresTo", "2026-07-31").cookie(cookie(viewer)))
                .andReturn();
        assertThat(JsonPath.<Integer>read(batchFilter.getResponse().getContentAsString(), "$.total")).isEqualTo(1);
        assertThat(JsonPath.<String>read(batchFilter.getResponse().getContentAsString(), "$.items[0].id"))
                .isEqualTo(expiring.toString());

        MvcResult assetFilter = mockMvc.perform(get("/api/v1/inventory/entries")
                        .queryParam("type", "ASSET").queryParam("assetStatus", "AVAILABLE")
                        .cookie(cookie(viewer)))
                .andReturn();
        assertThat(JsonPath.<Integer>read(assetFilter.getResponse().getContentAsString(), "$.total")).isEqualTo(1);
        assertThat(assetFilter.getResponse().getContentAsString()).contains(asset.toString());
    }

    @Test
    void returnsTypedDetailsMovementsAndAvailabilityToViewer() throws Exception {
        UUID batch = insertBatch("5");
        jdbc.update("update inventory_entry set production_date='2026-01-01', expiration_date='2026-02-01' where id=?",
                batch);
        UUID asset = insertAsset();
        assertThat(command("/api/v1/inventory/entries/" + batch + "/consume", "query-consume",
                "{\"quantity\":\"2\"}").getResponse().getStatus()).isEqualTo(200);
        String viewer = createSession("viewer", "VIEWER");

        MvcResult batchDetail = mockMvc.perform(get("/api/v1/inventory/entries/{id}", batch)
                        .cookie(cookie(viewer))).andReturn();
        assertThat(batchDetail.getResponse().getStatus()).isEqualTo(200);
        assertThat(batchDetail.getResponse().getContentAsString())
                .contains("\"batchNumber\":\"B-001\"")
                .contains("\"productionDate\":\"2026-01-01\"");

        MvcResult assetDetail = mockMvc.perform(get("/api/v1/inventory/entries/{id}", asset)
                        .cookie(cookie(viewer))).andReturn();
        assertThat(assetDetail.getResponse().getContentAsString())
                .contains("\"assetNumber\":\"ASSET-001\"")
                .contains("\"assetStatus\":\"AVAILABLE\"");

        MvcResult movementPage = mockMvc.perform(get("/api/v1/inventory/entries/{id}/movements", batch)
                        .cookie(cookie(viewer))).andReturn();
        assertThat(movementPage.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(movementPage.getResponse().getContentAsString(), "$[0].type"))
                .isEqualTo("CONSUME");
        assertThat(JsonPath.<String>read(movementPage.getResponse().getContentAsString(), "$[1].type"))
                .isEqualTo("RECEIVE");

        MvcResult availability = mockMvc.perform(get("/api/v1/inventory/availability")
                        .queryParam("itemId", itemId.toString()).cookie(cookie(viewer))).andReturn();
        assertThat(availability.getResponse().getStatus()).isEqualTo(200);
        assertThat(availability.getResponse().getContentAsString())
                .contains("\"totalAvailable\":\"4\"")
                .contains("\"activeEntryCount\":2")
                .contains("\"earliestExpiration\":\"2026-02-01\"");
    }

    @Test
    void hidesArchivedAndUnknownEntriesAndRestrictsArchivedListingToAdmin() throws Exception {
        UUID archived = insertBatch("1");
        jdbc.update("update inventory_entry set archived_at=now() where id=?", archived);
        String viewer = createSession("viewer", "VIEWER");

        MvcResult defaultList = mockMvc.perform(get("/api/v1/inventory/entries").cookie(cookie(viewer))).andReturn();
        assertThat(JsonPath.<Integer>read(defaultList.getResponse().getContentAsString(), "$.total")).isZero();
        assertThat(mockMvc.perform(get("/api/v1/inventory/entries/{id}", UUID.randomUUID())
                        .cookie(cookie(viewer))).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mockMvc.perform(get("/api/v1/inventory/entries")
                        .queryParam("includeArchived", "true").cookie(cookie(viewer)))
                .andReturn().getResponse().getStatus()).isEqualTo(403);

        String admin = createSession("admin", "ADMIN");
        MvcResult adminList = mockMvc.perform(get("/api/v1/inventory/entries")
                        .queryParam("includeArchived", "true").cookie(cookie(admin))).andReturn();
        assertThat(JsonPath.<Integer>read(adminList.getResponse().getContentAsString(), "$.total")).isEqualTo(1);
    }
}
