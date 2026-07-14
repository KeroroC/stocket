package com.stocket.reminder.internal.lifecycle;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    @Query("""
            select reminder from Reminder reminder
            where reminder.householdId = :householdId
              and reminder.itemDefinitionId = :itemId
              and reminder.status in ('SCHEDULED', 'OPEN', 'ACKNOWLEDGED')
              and (reminder.inventoryEntryId = :entryId or reminder.inventoryEntryId is null)
            """)
    List<Reminder> findActiveForRecalculation(
            @Param("householdId") UUID householdId,
            @Param("itemId") UUID itemId,
            @Param("entryId") UUID entryId);
}
