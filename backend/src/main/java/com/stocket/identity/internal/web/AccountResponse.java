package com.stocket.identity.internal.web;

import java.util.UUID;

import com.stocket.identity.IdentityRole;

public record AccountResponse(
        UUID id,
        String username,
        String displayName,
        String email,
        IdentityRole role,
        boolean mustChangePassword
) {
}
