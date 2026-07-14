package com.stocket.inventory.internal.query;

import java.util.List;
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
import com.stocket.inventory.InventoryExportQuery;
import com.stocket.inventory.InventoryExportRow;
import com.stocket.inventory.InventoryFilter;

@Service
public class InventoryQueryService implements InventoryQuery, InventoryExportQuery {

    private final InventoryQueryRepository repository;
    private final CurrentHouseholdProvider currentHouseholdProvider;

    InventoryQueryService(InventoryQueryRepository repository,
                          CurrentHouseholdProvider currentHouseholdProvider) {
        this.repository = repository;
        this.currentHouseholdProvider = currentHouseholdProvider;
    }

    InventoryQueryRepository.EntryPage entries(InventoryFilter filter, int page, int size) {
        CurrentHousehold current = currentHouseholdProvider.requireCurrent();
        if (filter.includeArchived() && current.role() != IdentityRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        validate(filter, page, size);
        return repository.findEntries(current.householdId(), entryFilter(filter, page, size));
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

    @Override public boolean existsEntry(UUID householdId, UUID entryId) {
        return repository.findEntry(householdId, entryId).isPresent();
    }

    @Override public long countForExport(UUID householdId, InventoryFilter filter) {
        filter.validate();
        return repository.countEntries(householdId, entryFilter(filter, 0, 1));
    }

    @Override public List<InventoryExportRow> exportPage(UUID householdId, InventoryFilter filter, UUID afterId, int size) {
        filter.validate();
        return repository.exportEntries(householdId, entryFilter(filter, 0, size), afterId, size).stream()
                .map(row -> new InventoryExportRow(row.id(), row.itemId(), row.itemName(), row.locationId(),
                        row.locationName(), row.type(), row.quantity(), row.receivedAt(), row.productionDate(),
                        row.expirationDate(), row.batchNumber(), row.assetNumber(), row.serialNumber(),
                        row.assetStatus(), row.archived()))
                .toList();
    }

    private void validate(InventoryFilter filter, int page, int size) {
        try { filter.validate(); } catch (IllegalArgumentException error) { throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY); }
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private InventoryQueryRepository.EntryFilter entryFilter(InventoryFilter filter, int page, int size) {
        return new InventoryQueryRepository.EntryFilter(filter.itemId(), filter.locationId(), filter.normalizedType(),
                filter.normalizedAssetStatus(), filter.expiresFrom(), filter.expiresTo(), filter.includeArchived(), page, size);
    }
}
