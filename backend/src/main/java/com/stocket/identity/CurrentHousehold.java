package com.stocket.identity;

import java.util.UUID;

public record CurrentHousehold(UUID householdId, UUID memberId, IdentityRole role) {
}
