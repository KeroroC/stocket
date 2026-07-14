package com.stocket.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.NamedInterface;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.test.context.aot.DisabledInAotMode;

import com.stocket.StocketApplication;
import com.stocket.catalog.CatalogInventoryQuery;
import com.stocket.identity.CurrentHousehold;
import com.stocket.location.LocationInventoryQuery;
import com.stocket.audit.AuditEvent;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledInAotMode
class InventoryModuleTest {

    @Test
    void inventoryOnlyDependsOnDeclaredPublicApis() {
        ApplicationModules.of(StocketApplication.class).verify();

        ApplicationModule inventory = InventoryQuery.class.getPackage().getAnnotation(ApplicationModule.class);
        assertThat(inventory.allowedDependencies())
                .containsExactlyInAnyOrder("identity :: api", "catalog :: api", "location :: api", "audit :: api");
        assertApi(CurrentHousehold.class);
        assertApi(CatalogInventoryQuery.class);
        assertApi(LocationInventoryQuery.class);
        assertApi(AuditEvent.class);
    }

    private void assertApi(Class<?> publicType) {
        NamedInterface namedInterface = publicType.getAnnotation(NamedInterface.class);
        assertThat(namedInterface).isNotNull();
        assertThat(namedInterface.value()).contains("api");
    }
}
