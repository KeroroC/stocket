package com.stocket.identity.internal.web;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

public record ExtendInviteRequest(
        @NotNull Instant expiresAt
) {
}
