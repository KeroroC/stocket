package com.stocket.location.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record ResolveLocationCodeRequest(@NotBlank @Size(max = 128) String payload) { }
