package com.stocket.inventory.internal.domain;

public class InventoryRuleViolationException extends RuntimeException {

    private final String code;

    InventoryRuleViolationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
