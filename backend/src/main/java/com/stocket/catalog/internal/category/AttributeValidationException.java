package com.stocket.catalog.internal.category;

class AttributeValidationException extends RuntimeException {

    private final String key;

    AttributeValidationException(String key, String message) {
        super(message);
        this.key = key;
    }

    String key() {
        return key;
    }
}
