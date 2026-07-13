package com.stocket.location;

import java.util.Optional;
import java.util.UUID;

@org.springframework.modulith.NamedInterface("api")
public interface LocationInventoryQuery {

    Optional<LocationSummary> find(UUID householdId, UUID locationId);
}
