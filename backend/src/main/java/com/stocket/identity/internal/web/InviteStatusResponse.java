package com.stocket.identity.internal.web;

import java.time.Instant;

import com.stocket.identity.IdentityRole;

public record InviteStatusResponse(
        boolean available,
        IdentityRole role,
        Instant expiresAt
) {
}
