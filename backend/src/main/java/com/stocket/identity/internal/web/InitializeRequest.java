package com.stocket.identity.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InitializeRequest(
        @NotBlank @Size(max = 120) String householdName,
        @NotBlank @Size(max = 80) String timezone,
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank String password) {
}
