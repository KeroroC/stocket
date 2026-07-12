package com.stocket.identity.internal.authentication;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {

    private static final int MIN_LENGTH = 12;
    private static final int MAX_LENGTH = 128;

    public List<String> validate(String normalizedUsername, String password) {
        List<String> errors = new ArrayList<>();
        if (password == null || password.length() < MIN_LENGTH) {
            errors.add("PASSWORD_TOO_SHORT");
        }
        if (password != null && password.length() > MAX_LENGTH) {
            errors.add("PASSWORD_TOO_LONG");
        }
        if (password != null && normalizedUsername != null
                && password.toLowerCase(Locale.ROOT).equals(normalizedUsername.toLowerCase(Locale.ROOT))) {
            errors.add("PASSWORD_EQUALS_USERNAME");
        }
        return errors;
    }
}
