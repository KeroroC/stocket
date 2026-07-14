package com.stocket.audit.internal.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class AuditDetailsPolicy {
    private static final int MAX_STRING = 500;
    private static final Set<String> SENSITIVE = Set.of("password", "token", "secret", "body", "authorization", "cookie");
    private static final Set<String> IDENTITY_FIELDS = Set.of("usernameFingerprint", "inviteId", "role", "targetMemberId",
            "targetAccountId", "sessionId", "newExpiry", "oldRole", "newRole", "status", "reason");
    private static final Map<String, Set<String>> ALLOWED = Map.ofEntries(
            Map.entry("AttachmentUploaded", Set.of("ownerType", "ownerId", "purpose", "filename", "mediaType", "sizeBytes")),
            Map.entry("AttachmentDeleted", Set.of("ownerType", "ownerId", "purpose")),
            Map.entry("InventoryReceived", Set.of("entryId", "itemId", "locationId", "quantity", "type")),
            Map.entry("InventoryTransferred", Set.of("entryId", "relatedEntryId", "fromLocationId", "toLocationId", "quantity")),
            Map.entry("InventoryChanged", Set.of("entryId", "operation", "quantity", "status")));

    public Map<String, Object> sanitize(String eventType, Map<String, Object> details) {
        Map<String, Object> source = details == null ? Map.of() : details;
        assertSafe(source);
        Set<String> allowed = ALLOWED.getOrDefault(eventType, IDENTITY_FIELDS);
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            Object bounded = bound(value);
            if (allowed.contains(key) && bounded != null) result.put(key, bounded);
        });
        return Map.copyOf(result);
    }

    private void assertSafe(Object value) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, nested) -> {
                String normalized = String.valueOf(key).toLowerCase(Locale.ROOT);
                if (SENSITIVE.stream().anyMatch(normalized::contains)) throw new IllegalArgumentException("AUDIT_DETAILS_SENSITIVE");
                assertSafe(nested);
            });
        } else if (value instanceof Iterable<?> iterable) {
            iterable.forEach(this::assertSafe);
        }
    }

    private Object bound(Object value) {
        if (value instanceof String text) return text.length() <= MAX_STRING ? text : text.substring(0, MAX_STRING);
        if (value instanceof Number || value instanceof Boolean || value == null) return value;
        if (value instanceof List<?> list) return list.stream().limit(50).map(this::bound).toList();
        return bound(String.valueOf(value));
    }
}
