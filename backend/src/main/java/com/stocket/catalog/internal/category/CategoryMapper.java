package com.stocket.catalog.internal.category;

final class CategoryMapper {

    private CategoryMapper() {
    }

    static CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.id(),
                category.parent() == null ? null : category.parent().id(),
                category.name(),
                category.defaultInventoryType(),
                category.attributeSchema(),
                category.version(),
                category.archived());
    }
}
