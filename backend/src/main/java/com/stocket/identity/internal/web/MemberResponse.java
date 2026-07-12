package com.stocket.identity.internal.web;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.stocket.identity.IdentityRole;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MemberResponse(
        UUID id,
        String username,
        String displayName,
        IdentityRole role,
        String temporaryPassword
) {
}
