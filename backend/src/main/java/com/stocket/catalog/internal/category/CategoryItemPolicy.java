package com.stocket.catalog.internal.category;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class CategoryItemPolicy {

    private final CategoryRepository repository;
    private final AttributeSchemaValidator validator;
    CategoryItemPolicy(CategoryRepository repository, AttributeSchemaValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    public void requireActive(UUID householdId, UUID categoryId) {
        Category category = repository.findByHouseholdIdAndId(householdId, categoryId)
                .orElseThrow(CategoryNotFoundException::new);
        if (category.archived()) {
            throw new CategoryArchivedException();
        }
    }

    public Map<String, Object> validateAttributes(UUID householdId, UUID categoryId,
                                                   Map<String, Object> suppliedValues) {
        Category category = repository.findByHouseholdIdAndId(householdId, categoryId)
                .orElseThrow(CategoryNotFoundException::new);
        if (category.archived()) {
            throw new CategoryArchivedException();
        }
        try {
            return validator.validateValues(category.attributeSchema(), suppliedValues);
        } catch (AttributeValidationException exception) {
            throw new InvalidItemAttributesException(exception.getMessage());
        }
    }

    public static class CategoryNotFoundException extends RuntimeException {
    }

    public static class CategoryArchivedException extends RuntimeException {
    }

    public static class InvalidItemAttributesException extends RuntimeException {
        InvalidItemAttributesException(String message) {
            super(message);
        }
    }
}
