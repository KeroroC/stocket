package com.stocket.reminder.internal.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.stocket.inventory.InventoryChanged;
import com.stocket.reminder.internal.lifecycle.ReminderRecalculator;

@Component
class InventoryChangedListener {

    private final ReminderRecalculator recalculator;

    InventoryChangedListener(ReminderRecalculator recalculator) {
        this.recalculator = recalculator;
    }

    @ApplicationModuleListener(id = "reminder.inventory-changed")
    void on(InventoryChanged event) {
        recalculator.recalculate(event.householdId(), event.itemId(), event.entryId());
    }
}
