package com.stocket.identity.internal.web;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

import com.stocket.identity.IdentityRole;

public record CreateInviteRequest(
        @NotNull IdentityRole role,
        Instant expiresAt,
        Integer maxUses
) {
}
