package com.stocket.identity.internal.authentication;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void rejectsTooShortPassword() {
        assertThat(policy.validate("owner", "short"))
                .containsExactly("PASSWORD_TOO_SHORT");
    }

    @Test
    void acceptsValidPassword() {
        assertThat(policy.validate("owner", "correct horse battery staple"))
                .isEmpty();
    }

    @Test
    void rejectsPasswordEqualToNormalizedUsername() {
        assertThat(policy.validate("owner", "owner"))
                .contains("PASSWORD_TOO_SHORT", "PASSWORD_EQUALS_USERNAME");
    }

    @Test
    void rejectsTooLongPassword() {
        String longPassword = "a".repeat(129);
        assertThat(policy.validate("owner", longPassword))
                .containsExactly("PASSWORD_TOO_LONG");
    }

    @Test
    void handlesNullPassword() {
        assertThat(policy.validate("owner", null))
                .containsExactly("PASSWORD_TOO_SHORT");
    }
}
