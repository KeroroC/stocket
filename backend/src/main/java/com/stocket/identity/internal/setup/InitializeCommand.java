package com.stocket.identity.internal.setup;

public record InitializeCommand(
        String householdName,
        String timezone,
        String username,
        String displayName,
        String password) {
}
