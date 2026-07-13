package com.stocket.identity.internal.web;

import jakarta.validation.constraints.NotNull;

import com.stocket.identity.IdentityRole;

public record UpdateMemberRequest(
        @NotNull IdentityRole role
) {
}
