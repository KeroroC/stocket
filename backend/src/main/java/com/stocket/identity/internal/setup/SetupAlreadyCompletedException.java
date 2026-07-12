package com.stocket.identity.internal.setup;

public class SetupAlreadyCompletedException extends RuntimeException {

    public SetupAlreadyCompletedException(String message) {
        super(message);
    }
}
