package com.stocket.inventory.internal.web;

import com.stocket.inventory.internal.command.ReceiveInventoryService;
import com.stocket.inventory.internal.idempotency.IdempotentExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryCommandController {

    private final ReceiveInventoryService receiveInventory;

    public InventoryCommandController(ReceiveInventoryService receiveInventory) {
        this.receiveInventory = receiveInventory;
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
