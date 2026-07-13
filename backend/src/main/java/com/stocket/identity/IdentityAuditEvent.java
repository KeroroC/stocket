package com.stocket.identity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@org.springframework.modulith.NamedInterface("api")
public record IdentityAuditEvent(
        UUID eventId,
        Instant occurredAt,
        String eventType,
        String outcome,
        UUID actorAccountId,
        String subjectType,
        UUID subjectId,
        String requestId,
        String source,
        Map<String, Object> details) {
    public IdentityAuditEvent {
        details = Map.copyOf(details);
    }
}
