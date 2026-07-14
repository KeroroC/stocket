package com.stocket.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@org.springframework.modulith.NamedInterface("api")
public record AuditEvent(UUID eventId, UUID householdId, Instant occurredAt, String eventType,
                         String outcome, UUID actorAccountId, String subjectType, UUID subjectId,
                         String requestId, String source, Map<String, Object> details) {
    public AuditEvent { details = details == null ? Map.of() : Map.copyOf(details); }
}
