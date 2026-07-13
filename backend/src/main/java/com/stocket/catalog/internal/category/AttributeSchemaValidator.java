package com.stocket.catalog.internal.category;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
class AttributeSchemaValidator {

    private static final Pattern KEY = Pattern.compile("[a-z][A-Za-z0-9]{0,63}");

    void validateSchema(List<AttributeDefinition> schema) {
        Set<String> keys = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (AttributeDefinition definition : schema) {
            if (definition.key() == null || !KEY.matcher(definition.key()).matches()) {
                throw invalid(definition.key(), "Invalid attribute key");
            }
            if (!keys.add(definition.key())) {
                throw invalid(definition.key(), "Duplicate attribute key: " + definition.key());
            }
            if (definition.label() == null || definition.label().isBlank() || definition.label().length() > 80) {
                throw invalid(definition.key(), "Invalid attribute label");
            }
            if (definition.order() < 0 || !orders.add(definition.order())) {
                throw invalid(definition.key(), "Invalid or duplicate attribute order");
            }
            if (definition.type() == AttributeType.ENUM) {
                if (definition.options().isEmpty()
                        || new HashSet<>(definition.options()).size() != definition.options().size()) {
                    throw invalid(definition.key(), "Enum options must be non-empty and unique");
                }
            }
            if (definition.defaultValue() != null) {
                validateValue(definition, definition.defaultValue());
            }
        }
    }

    Map<String, Object> validateValues(List<AttributeDefinition> schema, Map<String, Object> suppliedValues) {
        validateSchema(schema);
        Map<String, AttributeDefinition> definitions = new HashMap<>();
        schema.forEach(definition -> definitions.put(definition.key(), definition));
        suppliedValues.keySet().stream()
                .filter(key -> !definitions.containsKey(key))
                .findFirst()
                .ifPresent(key -> { throw invalid(key, "Unknown attribute: " + key); });

        Map<String, Object> result = new LinkedHashMap<>(suppliedValues);
        for (AttributeDefinition definition : schema) {
            Object value = result.get(definition.key());
            if (value == null && definition.defaultValue() != null) {
                value = definition.defaultValue();
                result.put(definition.key(), value);
            }
            if (value == null) {
                if (definition.required()) {
                    throw invalid(definition.key(), "Required attribute missing: " + definition.key());
                }
                continue;
            }
            validateValue(definition, value);
        }
        return Collections.unmodifiableMap(result);
    }

    private void validateValue(AttributeDefinition definition, Object value) {
        boolean valid = switch (definition.type()) {
            case TEXT -> value instanceof String;
            case NUMBER -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case DATE -> isIsoDate(value);
            case ENUM -> value instanceof String text && definition.options().contains(text);
        };
        if (!valid) {
            throw invalid(definition.key(), "Invalid value for attribute: " + definition.key());
        }
    }

    private boolean isIsoDate(Object value) {
        if (!(value instanceof String text)) {
            return false;
        }
        try {
            LocalDate.parse(text);
            return true;
        } catch (DateTimeException exception) {
            return false;
        }
    }

    private AttributeValidationException invalid(String key, String message) {
        return new AttributeValidationException(key, message);
    }
}
