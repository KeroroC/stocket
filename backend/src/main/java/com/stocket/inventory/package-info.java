@org.springframework.modulith.ApplicationModule(
        displayName = "Inventory",
        allowedDependencies = {"identity :: api", "catalog :: api", "location :: api", "audit :: api"})
package com.stocket.inventory;
