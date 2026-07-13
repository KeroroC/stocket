package com.stocket.identity.internal.authentication;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationServiceTest {

    @Test
    void dummyPasswordHashContainsAValidBcryptPayload() throws Exception {
        var field = AuthenticationService.class.getDeclaredField("DUMMY_PASSWORD_HASH");
        field.setAccessible(true);
        String encoded = (String) field.get(null);
        String bcryptPayload = encoded.substring("{bcrypt}".length());

        assertThat(bcryptPayload)
                .hasSize(60)
                .matches("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
    }
}
