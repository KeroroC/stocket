package com.stocket.catalog.internal.category;

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
@RequestMapping("/api/v1/categories")
class CategoryController {

    private final CategoryService service;

    CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    List<CategoryResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.listTree(includeArchived);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    CategoryResponse archive(@PathVariable UUID id, @RequestParam long version) {
        return service.archive(id, version);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    CategoryResponse restore(@PathVariable UUID id, @RequestParam long version) {
        return service.restore(id, version);
    }
}
