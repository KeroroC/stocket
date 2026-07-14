package com.stocket.system.operations;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.stocket.audit.AuditEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestMetricsTest {
    @Test void recordsOnlyLowCardinalityOperationAndOutcomeTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RequestMetrics metrics = new RequestMetrics(registry);
        metrics.on(new AuditEvent(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), "InventoryReceived", "SUCCESS",
                UUID.randomUUID(), "INVENTORY_ENTRY", UUID.randomUUID(), "request-unique-123", "api", Map.of()));

        assertThat(registry.get("stocket.business.operations").tags("operation", "InventoryReceived", "outcome", "SUCCESS")
                .counter().count()).isEqualTo(1);
        assertThat(registry.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags())
                .noneSatisfy(tag -> assertThat(tag.getKey()).isIn("requestId", "accountId", "itemId", "subjectId")));
    }
}
