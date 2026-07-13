package com.stocket.location.internal;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.UUID;

import com.stocket.location.LocationInventoryQuery;
import com.stocket.location.LocationSummary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class LocationInventoryLookup implements LocationInventoryQuery {

    private final LocationRepository locations;

    LocationInventoryLookup(LocationRepository locations) {
        this.locations = locations;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LocationSummary> find(UUID householdId, UUID locationId) {
        return locations.findByHouseholdIdAndId(householdId, locationId)
                .map(location -> new LocationSummary(
                        location.id(), location.name(), fullPath(location), location.archived()));
    }

    private String fullPath(Location location) {
        ArrayDeque<String> names = new ArrayDeque<>();
        for (Location current = location; current != null; current = current.parent()) {
            names.addFirst(current.name());
        }
        return String.join(" / ", names);
    }
}
