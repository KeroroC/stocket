package com.stocket.location;

import java.util.Optional;
import java.util.UUID;

public interface LocationInventoryQuery {

    Optional<LocationSummary> find(UUID householdId, UUID locationId);
}
