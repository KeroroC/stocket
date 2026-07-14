package com.stocket.notification.internal.channel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationChannelRepository extends JpaRepository<NotificationChannel, UUID> {

    List<NotificationChannel> findByHouseholdIdOrderByType(UUID householdId);

    Optional<NotificationChannel> findByHouseholdIdAndType(UUID householdId, String type);

    Optional<NotificationChannel> findByHouseholdIdAndId(UUID householdId, UUID id);
}
