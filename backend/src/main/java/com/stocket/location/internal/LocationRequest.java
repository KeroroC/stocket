package com.stocket.location.internal;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record LocationRequest(@NotBlank @Size(max = 120) String name, UUID parentId, Long version) { }
