package com.stocket.identity.internal.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.stocket.identity.CurrentHousehold;
import com.stocket.identity.CurrentHouseholdProvider;

@Component
public class SecurityCurrentHouseholdProvider implements CurrentHouseholdProvider {

    @Override
    public CurrentHousehold requireCurrent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof IdentityPrincipal principal)) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }

        return new CurrentHousehold(principal.householdId(), principal.memberId(), principal.role());
    }
}
