package com.stocket.location.internal;

import java.util.List;
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
@RequestMapping("/api/v1/locations")
class LocationController {
    private final LocationService service;
    LocationController(LocationService service) { this.service = service; }
    @GetMapping List<LocationResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.list(includeArchived);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')")
    LocationResponse create(@Valid @RequestBody LocationRequest request) { return service.create(request); }
    @PatchMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    LocationResponse update(@PathVariable UUID id, @Valid @RequestBody LocationRequest request) {
        return service.update(id, request);
    }
    @PostMapping("/{id}/archive") @PreAuthorize("hasRole('ADMIN')")
    LocationResponse archive(@PathVariable UUID id, @RequestParam long version) { return service.archive(id, version); }
    @PostMapping("/{id}/restore") @PreAuthorize("hasRole('ADMIN')")
    LocationResponse restore(@PathVariable UUID id, @RequestParam long version) { return service.restore(id, version); }
    @PostMapping("/resolve-code")
    LocationResponse resolve(@Valid @RequestBody ResolveLocationCodeRequest request) { return service.resolve(request.payload()); }
}
