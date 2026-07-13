package com.stocket;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.test.context.aot.DisabledInAotMode;

import static org.assertj.core.api.Assertions.assertThat;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@DisabledInAotMode
class ArchitectureTest {

    private final ApplicationModules modules = ApplicationModules.of(StocketApplication.class);

    @Test
    void moduleDependenciesAreValid() {
        // Verifies all module boundaries: internal packages are not accessible across modules,
        // and only declared dependencies are allowed. This covers:
        // - audit only depends on identity's public API (IdentityAuditEvent)
        // - identity does not depend on audit
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

    @Test
    void auditModuleDependsOnIdentity() {
        ApplicationModule auditModule = modules.getModuleByName("audit").orElseThrow();
        ApplicationModule identityModule = modules.getModuleByName("identity").orElseThrow();

        assertThat(auditModule.getDependencies(modules).contains(identityModule)).isTrue();
    }

    @Test
    void identityModuleDoesNotDependOnAudit() {
        ApplicationModule identityModule = modules.getModuleByName("identity").orElseThrow();

        assertThat(identityModule.getDependencies(modules).containsModuleNamed("audit")).isFalse();
    }

    @Test
    void catalogAndLocationDoNotUseIdentityInternals() {
        noClasses().that().resideInAnyPackage("com.stocket.catalog..", "com.stocket.location..")
                .should().dependOnClassesThat().resideInAPackage("com.stocket.identity.internal..")
                .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("com.stocket"));
    }

    @Test
    void inventoryDoesNotUseOtherModuleInternals() {
        noClasses().that().resideInAPackage("com.stocket.inventory..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.stocket.identity.internal..",
                        "com.stocket.catalog.internal..",
                        "com.stocket.location.internal..")
                .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("com.stocket"));
    }
}
