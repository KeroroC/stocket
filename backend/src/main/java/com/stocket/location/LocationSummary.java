package com.stocket.location;

import java.util.UUID;

public record LocationSummary(UUID id, String name, String fullPath, boolean archived) {
}
