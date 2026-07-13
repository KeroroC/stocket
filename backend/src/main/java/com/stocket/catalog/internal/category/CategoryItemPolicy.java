package com.stocket.catalog.internal.category;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class CategoryItemPolicy {

    private final CategoryRepository repository;
    private final AttributeSchemaValidator validator;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    public Map<String, JsonNode> validateAttributes(UUID householdId, UUID categoryId,
                                                     Map<String, Object> suppliedValues) {
        Category category = repository.findByHouseholdIdAndId(householdId, categoryId)
                .orElseThrow(CategoryNotFoundException::new);
        if (category.archived()) {
            throw new CategoryArchivedException();
        }
        Map<String, JsonNode> converted = new LinkedHashMap<>();
        suppliedValues.forEach((key, value) -> converted.put(key, objectMapper.valueToTree(value)));
        try {
            return validator.validateValues(category.attributeSchema(), converted);
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
