package com.stocket.notification;

import java.time.Instant;
import java.util.UUID;

@org.springframework.modulith.NamedInterface("api")
public record NotificationRequested(UUID reminderId, UUID householdId, Instant requestedAt, String requestId) {
}
