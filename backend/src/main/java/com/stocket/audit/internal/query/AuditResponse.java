package com.stocket.audit.internal.query;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AuditResponse(List<AuditEntry> items, String nextCursor) {
    public record AuditEntry(UUID id, Instant occurredAt, String eventType, String outcome,
                             UUID actorAccountId, String actorDisplayName, String subjectType, UUID subjectId,
                             String requestId, String source, Map<String, Object> details) { }
}
