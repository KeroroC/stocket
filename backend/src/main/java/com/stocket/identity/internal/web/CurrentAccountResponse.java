package com.stocket.identity.internal.web;

import java.util.UUID;

import com.stocket.identity.IdentityRole;

public record CurrentAccountResponse(UUID accountId, String username, IdentityRole role) {
}
