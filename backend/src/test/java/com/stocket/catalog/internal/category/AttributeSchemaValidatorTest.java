package com.stocket.catalog.internal.category;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttributeSchemaValidatorTest {

    private final AttributeSchemaValidator validator = new AttributeSchemaValidator();

    @Test
    void rejectsDuplicateAndInvalidSchemaKeys() {
        AttributeDefinition valid = definition("storageTemperature", AttributeType.NUMBER, true,
                4, List.of(), 10);

        assertThatThrownBy(() -> validator.validateSchema(List.of(valid, valid)))
                .isInstanceOf(AttributeValidationException.class)
                .hasMessageContaining("storageTemperature");
        assertThatThrownBy(() -> validator.validateSchema(List.of(
                definition("Storage-temperature", AttributeType.TEXT, false, null, List.of(), 0))))
                .isInstanceOf(AttributeValidationException.class);
    }

    @Test
    void rejectsInvalidEnumAndNumberDefaults() {
        assertThatThrownBy(() -> validator.validateSchema(List.of(
                definition("level", AttributeType.ENUM, false, null, List.of(), 0))))
                .isInstanceOf(AttributeValidationException.class);
        assertThatThrownBy(() -> validator.validateSchema(List.of(
                definition("temperature", AttributeType.NUMBER, false,
                        "cold", List.of(), 0))))
                .isInstanceOf(AttributeValidationException.class);
    }

    @Test
    void appliesDefaultsAndRejectsMissingUnknownOrInvalidValues() {
        List<AttributeDefinition> schema = List.of(
                definition("temperature", AttributeType.NUMBER, true, 4, List.of(), 0),
                definition("opened", AttributeType.BOOLEAN, true, null, List.of(), 1),
                definition("expiresOn", AttributeType.DATE, false, null, List.of(), 2),
                definition("level", AttributeType.ENUM, false, null, List.of("LOW", "HIGH"), 3));

        Map<String, Object> withDefaults = validator.validateValues(schema, Map.of(
                "opened", false,
                "expiresOn", "2026-07-13",
                "level", "LOW"));
        assertThat(withDefaults.get("temperature")).isEqualTo(4);

        assertThatThrownBy(() -> validator.validateValues(schema, Map.of()))
                .isInstanceOf(AttributeValidationException.class)
                .hasMessageContaining("opened");
        assertThatThrownBy(() -> validator.validateValues(schema, Map.of(
                "opened", false, "unknown", "x")))
                .isInstanceOf(AttributeValidationException.class)
                .hasMessageContaining("unknown");
        assertThatThrownBy(() -> validator.validateValues(schema, Map.of(
                "opened", false, "expiresOn", "2026-02-30")))
                .isInstanceOf(AttributeValidationException.class)
                .hasMessageContaining("expiresOn");
        assertThatThrownBy(() -> validator.validateValues(schema, Map.of(
                "opened", false, "level", "MEDIUM")))
                .isInstanceOf(AttributeValidationException.class)
                .hasMessageContaining("level");
    }

    private AttributeDefinition definition(String key, AttributeType type, boolean required,
                                           Object defaultValue, List<String> options, int order) {
        return new AttributeDefinition(key, key, type, required, defaultValue, options, order);
    }
}
