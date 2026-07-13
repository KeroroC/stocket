package com.stocket.identity.internal.authentication;

import java.util.UUID;

public record CreatedSession(UUID sessionId, String token) {
}
