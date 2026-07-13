package com.stocket.identity.internal.web;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CsrfController {

    @GetMapping("/api/v1/auth/csrf")
    CsrfTokenResponse csrf(CsrfToken csrfToken) {
        return new CsrfTokenResponse(
                csrfToken.getHeaderName(),
                csrfToken.getParameterName(),
                csrfToken.getToken());
    }

    record CsrfTokenResponse(String headerName, String parameterName, String token) {
    }
}
