package com.stocket.identity.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInviteRequest(
        @NotBlank @Size(max = 64) String username,
        @Size(max = 120) String displayName,
        @NotBlank String password
) {
}
