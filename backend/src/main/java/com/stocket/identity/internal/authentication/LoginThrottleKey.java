package com.stocket.identity.internal.authentication;

public record LoginThrottleKey(String normalizedUsername, String sourceAddress) {
}
