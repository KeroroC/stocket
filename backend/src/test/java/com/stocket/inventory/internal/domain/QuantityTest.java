package com.stocket.inventory.internal.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuantityTest {

    @Test
    void normalizesDecimalStringsWithoutUsingFloatingPoint() {
        assertThat(Quantity.of("1.2300").value()).isEqualByComparingTo("1.23");
        assertThat(Quantity.of("999999999999999.9999").value())
                .isEqualByComparingTo("999999999999999.9999");
    }

    @Test
    void rejectsZeroNegativeExcessScaleAndOutOfRangeValues() {
        assertThatThrownBy(() -> Quantity.of("0"))
                .isInstanceOf(InventoryRuleViolationException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> Quantity.of("-1"))
                .isInstanceOf(InventoryRuleViolationException.class);
        assertThatThrownBy(() -> Quantity.of("0.00001"))
                .isInstanceOf(InventoryRuleViolationException.class)
                .hasMessageContaining("decimal places");
        assertThatThrownBy(() -> Quantity.of("1000000000000000"))
                .isInstanceOf(InventoryRuleViolationException.class)
                .hasMessageContaining("range");
    }

    @Test
    void rejectsNonDecimalInput() {
        assertThatThrownBy(() -> Quantity.of("NaN"))
                .isInstanceOf(InventoryRuleViolationException.class)
                .hasMessageContaining("decimal");
    }
}
