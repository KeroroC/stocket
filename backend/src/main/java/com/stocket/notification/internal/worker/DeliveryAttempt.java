package com.stocket.notification.internal.worker;

import java.util.UUID;

public record DeliveryAttempt(
        UUID id,
        UUID householdId,
        UUID reminderId,
        UUID memberId,
        String channelType,
        UUID channelId,
        int attemptCount,
        String leaseOwner
) {
    public String deliveryKey() {
        return id.toString();
    }
}
