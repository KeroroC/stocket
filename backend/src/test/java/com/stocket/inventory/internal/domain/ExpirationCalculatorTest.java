package com.stocket.inventory.internal.domain;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExpirationCalculatorTest {

    private final ExpirationCalculator calculator = new ExpirationCalculator();

    @Test
    void explicitExpirationDateHasPriority() {
        assertThat(calculator.calculate(
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2026, 1, 31),
                new ShelfLife(1, ShelfLifeUnit.MONTH),
                new ShelfLife(2, ShelfLifeUnit.YEAR)))
                .contains(LocalDate.of(2027, 1, 1));
    }

    @Test
    void usesCalendarArithmeticForLeapYearsAndMonthEnds() {
        assertThat(calculator.calculate(null, LocalDate.of(2024, 2, 29),
                new ShelfLife(1, ShelfLifeUnit.YEAR), null))
                .contains(LocalDate.of(2025, 2, 28));
        assertThat(calculator.calculate(null, LocalDate.of(2026, 1, 31),
                new ShelfLife(1, ShelfLifeUnit.MONTH), null))
                .contains(LocalDate.of(2026, 2, 28));
    }

    @Test
    void fallsBackToCatalogShelfLifeAndRequiresProductionDate() {
        assertThat(calculator.calculate(null, LocalDate.of(2026, 7, 14), null,
                new ShelfLife(7, ShelfLifeUnit.DAY)))
                .contains(LocalDate.of(2026, 7, 21));
        assertThat(calculator.calculate(null, null, null,
                new ShelfLife(7, ShelfLifeUnit.DAY)))
                .isEmpty();
    }
}
