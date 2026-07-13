package com.stocket.identity.internal.web;

import jakarta.validation.constraints.NotBlank;

record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}
