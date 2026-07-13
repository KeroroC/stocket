package com.stocket.inventory.internal.query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stocket.inventory.InventoryItemAvailability;

@RestController
@RequestMapping("/api/v1/inventory")
class InventoryQueryController {

    private final InventoryQueryService service;

    InventoryQueryController(InventoryQueryService service) {
        this.service = service;
    }

    @GetMapping("/entries")
    InventoryQueryRepository.EntryPage entries(
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String assetStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresTo,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.entries(itemId, locationId, type, assetStatus, expiresFrom, expiresTo,
                includeArchived, page, size);
    }

    @GetMapping("/entries/{id}")
    InventoryEntryResponse entry(@PathVariable UUID id) {
        return service.entry(id);
    }

    @GetMapping("/entries/{id}/movements")
    List<MovementResponse> movements(@PathVariable UUID id) {
        return service.movements(id);
    }

    @GetMapping("/availability")
    AvailabilityResponse availability(@RequestParam UUID itemId) {
        InventoryItemAvailability availability = service.currentAvailability(itemId);
        return new AvailabilityResponse(availability.itemId(), decimal(availability.totalAvailable()),
                availability.earliestExpiration(), availability.activeEntryCount());
    }

    private String decimal(java.math.BigDecimal value) {
        if (value.signum() == 0) return "0";
        return value.stripTrailingZeros().toPlainString();
    }

    record AvailabilityResponse(UUID itemId, String totalAvailable,
                                LocalDate earliestExpiration, int activeEntryCount) { }
}
