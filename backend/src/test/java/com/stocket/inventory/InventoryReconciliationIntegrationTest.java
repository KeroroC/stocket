package com.stocket.inventory;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InventoryReconciliationIntegrationTest extends InventoryCommandTestSupport {

    @Test
    void recordsSnapshotMismatchWithoutRepairingAndResolvesItWhenRestored() throws Exception {
        UUID entryId = insertBatch("5");
        String admin = createSession("reconcile-admin", "ADMIN");

        assertThat(reconcile(admin)).isEqualTo(200);
        assertThat(issueCount()).isZero();

        jdbc.update("update inventory_entry set available_quantity=3 where id=?", entryId);
        assertThat(reconcile(admin)).isEqualTo(200);

        Map<String, Object> issue = jdbc.queryForMap("""
                select expected_quantity, actual_quantity, status, resolved_at
                from inventory_reconciliation_issue where entry_id=?
                """, entryId);
        assertThat(issue.get("expected_quantity")).isEqualTo(new BigDecimal("5.0000"));
        assertThat(issue.get("actual_quantity")).isEqualTo(new BigDecimal("3.0000"));
        assertThat(issue.get("status")).isEqualTo("OPEN");
        assertThat(issue.get("resolved_at")).isNull();
        assertThat(jdbc.queryForObject("select available_quantity from inventory_entry where id=?",
                BigDecimal.class, entryId)).isEqualByComparingTo("3");

        assertThat(reconcile(admin)).isEqualTo(200);
        assertThat(issueCount()).isEqualTo(1);

        jdbc.update("update inventory_entry set available_quantity=5 where id=?", entryId);
        assertThat(reconcile(admin)).isEqualTo(200);
        Map<String, Object> resolved = jdbc.queryForMap("""
                select status, resolved_at from inventory_reconciliation_issue where entry_id=?
                """, entryId);
        assertThat(resolved.get("status")).isEqualTo("RESOLVED");
        assertThat(resolved.get("resolved_at")).isNotNull();
    }

    @Test
    void onlyAdminCanTriggerReconciliation() throws Exception {
        assertThat(reconcile(session)).isEqualTo(403);
        assertThat(reconcile(createSession("reconcile-viewer", "VIEWER"))).isEqualTo(403);
    }

    private int reconcile(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/inventory/reconcile").with(csrf()).cookie(cookie(token)))
                .andReturn().getResponse().getStatus();
    }

    private int issueCount() {
        Integer count = jdbc.queryForObject("select count(*) from inventory_reconciliation_issue", Integer.class);
        return count == null ? 0 : count;
    }
}
