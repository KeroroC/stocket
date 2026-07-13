package com.stocket.identity.internal.security;

import java.util.UUID;

import com.stocket.identity.IdentityRole;

public record IdentityPrincipal(
        UUID accountId,
        UUID householdId,
        UUID memberId,
        String username,
        IdentityRole role,
        boolean mustChangePassword,
        UUID sessionId
) {
}
