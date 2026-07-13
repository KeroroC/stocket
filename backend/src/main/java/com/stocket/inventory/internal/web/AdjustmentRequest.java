package com.stocket.inventory.internal.web;

public record AdjustmentRequest(String targetQuantity, String reason) {
}
