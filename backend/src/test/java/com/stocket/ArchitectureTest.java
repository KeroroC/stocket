package com.stocket;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.test.context.aot.DisabledInAotMode;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledInAotMode
class ArchitectureTest {

    private final ApplicationModules modules = ApplicationModules.of(StocketApplication.class);

    @Test
    void moduleDependenciesAreValid() {
        modules.verify();
    }

    @Test
    void approvedModulesArePresent() {
        assertThat(modules.stream()
                .map(module -> module.getIdentifier().toString())
                .toList())
                .containsExactlyInAnyOrder(
                        "attachment",
                        "audit",
                        "catalog",
                        "identity",
                        "inventory",
                        "location",
                        "notification",
                        "reminder",
                        "system"
                );
    }
}
