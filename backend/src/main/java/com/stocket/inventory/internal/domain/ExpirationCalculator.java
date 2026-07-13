package com.stocket.inventory.internal.domain;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class ExpirationCalculator {

    public Optional<LocalDate> calculate(LocalDate explicitExpiration,
                                         LocalDate productionDate,
                                         ShelfLife requestedShelfLife,
                                         ShelfLife catalogShelfLife) {
        if (explicitExpiration != null) {
            return Optional.of(explicitExpiration);
        }
        if (productionDate == null) {
            return Optional.empty();
        }
        ShelfLife shelfLife = requestedShelfLife != null ? requestedShelfLife : catalogShelfLife;
        if (shelfLife == null) {
            return Optional.empty();
        }
        return Optional.of(switch (shelfLife.unit()) {
            case DAY -> productionDate.plusDays(shelfLife.value());
            case MONTH -> productionDate.plusMonths(shelfLife.value());
            case YEAR -> productionDate.plusYears(shelfLife.value());
        });
    }
}
