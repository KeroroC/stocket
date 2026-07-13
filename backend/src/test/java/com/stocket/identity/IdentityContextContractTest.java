package com.stocket.identity;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.aot.DisabledInAotMode;

import com.stocket.identity.internal.security.IdentityPrincipal;
import com.stocket.identity.internal.security.SecurityCurrentHouseholdProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityContextContractTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisabledInAotMode
    void publishesCurrentHouseholdContract() throws Exception {
        Class<?> currentHouseholdType = CurrentHousehold.class;
        assertThat(currentHouseholdType.isRecord()).isTrue();
        assertThat(Modifier.isPublic(currentHouseholdType.getModifiers())).isTrue();
        assertThat(List.of(currentHouseholdType.getRecordComponents())
                .stream()
                .map(component -> component.getName() + ":" + component.getType().getSimpleName()))
                .containsExactly("householdId:UUID", "memberId:UUID", "role:IdentityRole");

        Class<?> providerType = CurrentHouseholdProvider.class;
        assertThat(providerType.isInterface()).isTrue();
        assertThat(Modifier.isPublic(providerType.getModifiers())).isTrue();
        assertThat(providerType.getMethod("requireCurrent").getReturnType())
                .isEqualTo(currentHouseholdType);
    }

    @Test
    void mapsAuthenticatedIdentityPrincipalToCurrentHousehold() {
        UUID householdId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        IdentityPrincipal principal = new IdentityPrincipal(
                UUID.randomUUID(), householdId, memberId, "member",
                IdentityRole.MEMBER, false, UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));

        CurrentHousehold current = new SecurityCurrentHouseholdProvider().requireCurrent();

        assertThat(current.householdId()).isEqualTo(householdId);
        assertThat(current.memberId()).isEqualTo(memberId);
        assertThat(current.role()).isEqualTo(IdentityRole.MEMBER);
    }

    @Test
    void anonymousAccessRequiresAuthentication() {
        CurrentHouseholdProvider provider = new SecurityCurrentHouseholdProvider();

        assertThatThrownBy(provider::requireCurrent)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
