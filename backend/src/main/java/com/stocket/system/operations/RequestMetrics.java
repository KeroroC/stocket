package com.stocket.system.operations;

import java.util.Set;

import com.stocket.audit.AuditEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class RequestMetrics {
    private static final Set<String> OPERATIONS = Set.of(
            "AttachmentUploaded", "AttachmentDeleted", "InventoryReceived", "InventoryTransferred",
            "InventoryChanged", "CatalogChanged", "LocationChanged", "ReminderChanged",
            "NotificationChannelChanged");
    private static final Set<String> OUTCOMES = Set.of("SUCCESS", "FAILURE", "REJECTED");
    private final MeterRegistry registry;

    public RequestMetrics(MeterRegistry registry) { this.registry = registry; }

    @EventListener
    public void on(AuditEvent event) {
        registry.counter("stocket.business.operations",
                "operation", bounded(event.eventType(), OPERATIONS),
                "outcome", bounded(event.outcome(), OUTCOMES)).increment();
    }

    private String bounded(String value, Set<String> allowed) {
        return value != null && allowed.contains(value) ? value : "OTHER";
    }
}
