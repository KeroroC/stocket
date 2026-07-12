package com.stocket.identity.internal.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 120) String displayName,
        @Email @Size(max = 254) String email
) {
}
