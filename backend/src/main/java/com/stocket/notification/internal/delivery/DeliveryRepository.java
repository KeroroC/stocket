package com.stocket.notification.internal.delivery;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {
}
