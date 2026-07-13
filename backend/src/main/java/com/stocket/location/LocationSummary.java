package com.stocket.location;

import java.util.UUID;

@org.springframework.modulith.NamedInterface("api")
public record LocationSummary(UUID id, String name, String fullPath, boolean archived) {
}
