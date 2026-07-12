package com.stocket;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic
@SpringBootApplication
public class StocketApplication {

    private static final String MAINTENANCE_RESET_ADMIN = "--stocket.maintenance.reset-admin=";

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(StocketApplication.class);

        // Check for maintenance command before creating context
        boolean isMaintenanceMode = Arrays.stream(args)
                .anyMatch(arg -> arg.startsWith(MAINTENANCE_RESET_ADMIN));

        if (isMaintenanceMode) {
            // In maintenance mode, disable web server entirely
            application.setWebApplicationType(WebApplicationType.NONE);
        }

        application.run(args);
    }
}
