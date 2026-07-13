package com.stocket.inventory.internal.reconciliation;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stocket.identity.CurrentHouseholdProvider;

@Service
public class InventoryReconciler {

    private static final int BATCH_SIZE = 500;

    private final ReconciliationRepository repository;
    private final Clock clock;

    InventoryReconciler(ReconciliationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public ReconciliationResult reconcile(UUID householdId) {
        int scanned = 0;
        int opened = 0;
        int resolved = 0;
        UUID cursor = null;
        Instant now = clock.instant();
        while (true) {
            List<ReconciliationRepository.EntrySnapshot> batch =
                    repository.findBatch(householdId, cursor, BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            for (ReconciliationRepository.EntrySnapshot snapshot : batch) {
                scanned++;
                if (snapshot.matches()) {
                    resolved += repository.resolve(householdId, snapshot.entryId(), now);
                } else if (repository.recordMismatch(householdId, snapshot, now)) {
                    opened++;
                }
            }
            cursor = batch.getLast().entryId();
            if (batch.size() < BATCH_SIZE) {
                break;
            }
        }
        return new ReconciliationResult(scanned, opened, resolved);
    }

    public record ReconciliationResult(int scannedEntries, int openedIssues, int resolvedIssues) { }
}

@RestController
@RequestMapping("/api/v1/admin/inventory")
class InventoryReconciliationController {

    private final InventoryReconciler reconciler;
    private final CurrentHouseholdProvider currentHouseholdProvider;

    InventoryReconciliationController(InventoryReconciler reconciler,
                                      CurrentHouseholdProvider currentHouseholdProvider) {
        this.reconciler = reconciler;
        this.currentHouseholdProvider = currentHouseholdProvider;
    }

    @PostMapping("/reconcile")
    InventoryReconciler.ReconciliationResult reconcile() {
        return reconciler.reconcile(currentHouseholdProvider.requireCurrent().householdId());
    }
}
