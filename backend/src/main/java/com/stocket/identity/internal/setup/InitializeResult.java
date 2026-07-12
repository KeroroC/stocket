package com.stocket.identity.internal.setup;

import java.util.UUID;

import com.stocket.identity.IdentityRole;

public record InitializeResult(UUID accountId, String username, IdentityRole role) {
}
