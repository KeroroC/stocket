package com.stocket.identity.internal.web;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.stocket.identity.IdentityRole;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InviteResponse(
        UUID id,
        IdentityRole role,
        Instant expiresAt,
        Instant acceptedAt,
        Instant revokedAt,
        Instant createdAt
) {
}
