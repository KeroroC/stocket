package com.stocket.identity;

import java.util.UUID;

public record CurrentHousehold(UUID householdId, UUID accountId, UUID memberId, IdentityRole role) {

    public CurrentHousehold(UUID householdId, UUID memberId, IdentityRole role) {
        this(householdId, memberId, memberId, role);
    }
}
