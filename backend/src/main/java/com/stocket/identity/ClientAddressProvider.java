package com.stocket.identity;

import jakarta.servlet.http.HttpServletRequest;

@FunctionalInterface
@org.springframework.modulith.NamedInterface("api")
public interface ClientAddressProvider {
    String resolve(HttpServletRequest request);
}
