package com.stocket.inventory.internal.web;

import java.util.UUID;

import com.stocket.inventory.internal.command.AdjustInventoryService;
import com.stocket.inventory.internal.command.AssetLifecycleService;
import com.stocket.inventory.internal.command.ConsumeInventoryService;
import com.stocket.inventory.internal.command.ReceiveInventoryService;
import com.stocket.inventory.internal.command.TransferInventoryService;
import com.stocket.inventory.internal.idempotency.IdempotentExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryCommandController {

    private final ReceiveInventoryService receiveInventory;
    private final ConsumeInventoryService consumeInventory;
    private final AdjustInventoryService adjustInventory;
    private final AssetLifecycleService assetLifecycle;
    private final TransferInventoryService transferInventory;

    public InventoryCommandController(ReceiveInventoryService receiveInventory,
                                      ConsumeInventoryService consumeInventory,
                                      AdjustInventoryService adjustInventory,
                                      AssetLifecycleService assetLifecycle,
                                      TransferInventoryService transferInventory) {
        this.receiveInventory = receiveInventory;
        this.consumeInventory = consumeInventory;
        this.adjustInventory = adjustInventory;
        this.assetLifecycle = assetLifecycle;
        this.transferInventory = transferInventory;
    }

    @PostMapping("/receipts")
    @PreAuthorize("hasAnyRole('ADMIN','MEMBER')")
    public ResponseEntity<InventoryCommandResponse> receive(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ReceiveInventoryRequest request) {
        String key = requireIdempotencyKey(idempotencyKey);
        IdempotentExecutor.Result<InventoryCommandResponse> result =
                receiveInventory.receive(key, request.toCommand());
        return ResponseEntity.status(result.httpStatus()).body(result.body());
    }

    @PostMapping("/entries/{id}/consume")
    @PreAuthorize("hasAnyRole('ADMIN','MEMBER')")
    public ResponseEntity<InventoryCommandResponse> consume(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ConsumeRequest request) {
        return response(consumeInventory.consume(id, requireIdempotencyKey(idempotencyKey), request.quantity()));
    }

    @PostMapping("/entries/{id}/return")
    @PreAuthorize("hasAnyRole('ADMIN','MEMBER')")
    public ResponseEntity<InventoryCommandResponse> returnInventory(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ConsumeRequest request) {
        return response(consumeInventory.returnInventory(
                id, requireIdempotencyKey(idempotencyKey), request.quantity(), request.reason()));
    }

    @PostMapping("/entries/{id}/adjust")
    @PreAuthorize("hasAnyRole('ADMIN','MEMBER')")
    public ResponseEntity<InventoryCommandResponse> adjust(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody AdjustmentRequest request) {
        return response(adjustInventory.adjust(
                id, requireIdempotencyKey(idempotencyKey), request.targetQuantity(), request.reason()));
    }

    @PostMapping("/entries/{id}/lost")
    @PreAuthorize("hasAnyRole('ADMIN','MEMBER')")
    public ResponseEntity<InventoryCommandResponse> lost(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody AssetStatusRequest request) {
        return response(assetLifecycle.lost(
                id, requireIdempotencyKey(idempotencyKey), request.reason()));
    }

    @PostMapping("/entries/{id}/retire")
    @PreAuthorize("hasAnyRole('ADMIN','MEMBER')")
    public ResponseEntity<InventoryCommandResponse> retire(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody AssetStatusRequest request) {
        return response(assetLifecycle.retire(
                id, requireIdempotencyKey(idempotencyKey), request.reason()));
    }

    @PostMapping("/entries/{id}/transfer")
    @PreAuthorize("hasAnyRole('ADMIN','MEMBER')")
    public ResponseEntity<InventoryCommandResponse> transfer(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody TransferRequest request) {
        return response(transferInventory.transfer(
                id, requireIdempotencyKey(idempotencyKey), request.targetLocationId(), request.quantity()));
    }

    private ResponseEntity<InventoryCommandResponse> response(
            IdempotentExecutor.Result<InventoryCommandResponse> result) {
        return ResponseEntity.status(result.httpStatus()).body(result.body());
    }

    private String requireIdempotencyKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IdempotentExecutor.IdempotencyException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED");
        }
        if (key.length() > 120 || key.chars().anyMatch(character -> character < 0x21 || character > 0x7e)) {
            throw new IdempotentExecutor.IdempotencyException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID");
        }
        return key;
    }
}
