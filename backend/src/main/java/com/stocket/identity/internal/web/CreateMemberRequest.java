package com.stocket.identity.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.stocket.identity.IdentityRole;

public record CreateMemberRequest(
        @NotBlank @Size(max = 64) String username,
        @Size(max = 120) String displayName,
        @NotNull IdentityRole role
) {
}
