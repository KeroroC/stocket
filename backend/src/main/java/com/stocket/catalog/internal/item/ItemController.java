package com.stocket.catalog.internal.item;

import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/items")
class ItemController {
    private final ItemService service;

    ItemController(ItemService service) { this.service = service; }

    @GetMapping("/{id}")
    ItemResponse get(@PathVariable UUID id) { return service.get(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MEMBER')")
    ItemResponse create(@Valid @RequestBody ItemRequest request) { return service.create(request); }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MEMBER')")
    ItemResponse update(@PathVariable UUID id, @Valid @RequestBody ItemRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('ADMIN','MEMBER')")
    ItemResponse archive(@PathVariable UUID id, @RequestParam long version) { return service.archive(id, version); }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('ADMIN','MEMBER')")
    ItemResponse restore(@PathVariable UUID id, @RequestParam long version) { return service.restore(id, version); }
}
