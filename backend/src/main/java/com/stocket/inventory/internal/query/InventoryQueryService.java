package com.stocket.inventory.internal.query;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.stocket.identity.CurrentHousehold;
import com.stocket.identity.CurrentHouseholdProvider;
import com.stocket.identity.IdentityRole;
import com.stocket.inventory.InventoryItemAvailability;
import com.stocket.inventory.InventoryQuery;

@Service
public class InventoryQueryService implements InventoryQuery {

    private final InventoryQueryRepository repository;
    private final CurrentHouseholdProvider currentHouseholdProvider;

    InventoryQueryService(InventoryQueryRepository repository,
                          CurrentHouseholdProvider currentHouseholdProvider) {
        this.repository = repository;
        this.currentHouseholdProvider = currentHouseholdProvider;
    }

    InventoryQueryRepository.EntryPage entries(UUID itemId, UUID locationId, String type,
                                                String assetStatus, LocalDate expiresFrom,
                                                LocalDate expiresTo, boolean includeArchived,
                                                int page, int size) {
        CurrentHousehold current = currentHouseholdProvider.requireCurrent();
        if (includeArchived && current.role() != IdentityRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        validate(type, assetStatus, expiresFrom, expiresTo, page, size);
        return repository.findEntries(current.householdId(), new InventoryQueryRepository.EntryFilter(
                itemId, locationId, normalize(type), normalize(assetStatus), expiresFrom, expiresTo,
                includeArchived, page, size));
    }

    InventoryEntryResponse entry(UUID entryId) {
        UUID householdId = currentHouseholdProvider.requireCurrent().householdId();
        return repository.findEntry(householdId, entryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    List<MovementResponse> movements(UUID entryId) {
        UUID householdId = currentHouseholdProvider.requireCurrent().householdId();
        if (repository.findEntry(householdId, entryId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return repository.findMovements(householdId, entryId);
    }

    InventoryItemAvailability currentAvailability(UUID itemId) {
        UUID householdId = currentHouseholdProvider.requireCurrent().householdId();
        return availability(householdId, itemId)
                .orElse(new InventoryItemAvailability(itemId, java.math.BigDecimal.ZERO, null, 0));
    }

    @Override
    public Optional<InventoryItemAvailability> availability(UUID householdId, UUID itemId) {
        return repository.availability(householdId, itemId);
    }

    private void validate(String type, String assetStatus, LocalDate expiresFrom,
                          LocalDate expiresTo, int page, int size) {
        if (page < 0 || size < 1 || size > 100
                || (type != null && !type.equalsIgnoreCase("BATCH") && !type.equalsIgnoreCase("ASSET"))
                || (assetStatus != null && !List.of("AVAILABLE", "IN_USE", "LOANED", "LOST", "RETIRED")
                        .contains(assetStatus.toUpperCase(Locale.ROOT)))
                || (expiresFrom != null && expiresTo != null && expiresFrom.isAfter(expiresTo))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }
}
