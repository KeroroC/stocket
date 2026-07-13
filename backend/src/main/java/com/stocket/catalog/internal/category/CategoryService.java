package com.stocket.catalog.internal.category;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stocket.identity.CurrentHouseholdProvider;

@Service
class CategoryService {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final CategoryRepository repository;
    private final CurrentHouseholdProvider currentHouseholdProvider;

    CategoryService(CategoryRepository repository, CurrentHouseholdProvider currentHouseholdProvider) {
        this.repository = repository;
        this.currentHouseholdProvider = currentHouseholdProvider;
    }

    @Transactional(readOnly = true)
    List<CategoryResponse> listTree(boolean includeArchived) {
        UUID householdId = currentHouseholdProvider.requireCurrent().householdId();
        List<Category> categories = repository.findByHouseholdId(householdId).stream()
                .filter(category -> includeArchived || !category.archived())
                .toList();
        List<CategoryResponse> result = new ArrayList<>();
        appendChildren(categories, null, result);
        return result;
    }

    @Transactional
    CategoryResponse create(CategoryRequest request) {
        UUID householdId = currentHouseholdProvider.requireCurrent().householdId();
        Category parent = resolveParent(householdId, request.parentId());
        String name = cleanName(request.name());
        String normalizedName = normalizeName(name);
        assertNameAvailable(householdId, parent, normalizedName, null);
        Category category = new Category(UUID.randomUUID(), householdId, parent, name, normalizedName,
                request.defaultInventoryType(), request.attributeSchema(), Instant.now());
        return CategoryMapper.toResponse(repository.saveAndFlush(category));
    }

    @Transactional
    CategoryResponse update(UUID id, CategoryRequest request) {
        UUID householdId = currentHouseholdProvider.requireCurrent().householdId();
        Category category = requireCategory(householdId, id);
        requireVersion(request.version(), category.version());
        Category parent = resolveParent(householdId, request.parentId());
        assertNoCycle(category, parent);
        String name = cleanName(request.name());
        String normalizedName = normalizeName(name);
        assertNameAvailable(householdId, parent, normalizedName, id);
        category.update(parent, name, normalizedName, request.defaultInventoryType(),
                request.attributeSchema(), Instant.now());
        return CategoryMapper.toResponse(repository.saveAndFlush(category));
    }

    @Transactional
    CategoryResponse archive(UUID id, long version) {
        UUID householdId = currentHouseholdProvider.requireCurrent().householdId();
        Category category = requireCategory(householdId, id);
        requireVersion(version, category.version());
        category.archive(Instant.now());
        return CategoryMapper.toResponse(repository.saveAndFlush(category));
    }

    @Transactional
    CategoryResponse restore(UUID id, long version) {
        UUID householdId = currentHouseholdProvider.requireCurrent().householdId();
        Category category = requireCategory(householdId, id);
        requireVersion(version, category.version());
        category.restore(Instant.now());
        return CategoryMapper.toResponse(repository.saveAndFlush(category));
    }

    private void appendChildren(List<Category> all, Category parent, List<CategoryResponse> result) {
        all.stream()
                .filter(category -> parent == null ? category.parent() == null : category.parent() != null
                        && category.parent().id().equals(parent.id()))
                .sorted(Comparator.comparing(Category::normalizedName).thenComparing(Category::id))
                .forEach(category -> {
                    result.add(CategoryMapper.toResponse(category));
                    appendChildren(all, category, result);
                });
    }

    private Category requireCategory(UUID householdId, UUID id) {
        return repository.findByHouseholdIdAndId(householdId, id)
                .orElseThrow(CategoryNotFoundException::new);
    }

    private Category resolveParent(UUID householdId, UUID parentId) {
        return parentId == null ? null : requireCategory(householdId, parentId);
    }

    private void assertNoCycle(Category category, Category proposedParent) {
        Category ancestor = proposedParent;
        while (ancestor != null) {
            if (ancestor.id().equals(category.id())) {
                throw new CategoryCycleException();
            }
            ancestor = ancestor.parent();
        }
    }

    private void assertNameAvailable(UUID householdId, Category parent, String normalizedName, UUID excludedId) {
        boolean exists = excludedId == null
                ? repository.existsByHouseholdIdAndParentAndNormalizedNameAndArchivedAtIsNull(
                        householdId, parent, normalizedName)
                : repository.existsByHouseholdIdAndParentAndNormalizedNameAndArchivedAtIsNullAndIdNot(
                        householdId, parent, normalizedName, excludedId);
        if (exists) {
            throw new CategoryNameConflictException();
        }
    }

    private void requireVersion(Long requestedVersion, long actualVersion) {
        if (requestedVersion == null || requestedVersion != actualVersion) {
            throw new CategoryVersionConflictException();
        }
    }

    private String cleanName(String name) {
        return WHITESPACE.matcher(name.strip()).replaceAll(" ");
    }

    private String normalizeName(String name) {
        return cleanName(name).toLowerCase(Locale.ROOT);
    }

    static class CategoryNotFoundException extends RuntimeException { }
    static class CategoryNameConflictException extends RuntimeException { }
    static class CategoryCycleException extends RuntimeException { }
    static class CategoryVersionConflictException extends RuntimeException { }
}
