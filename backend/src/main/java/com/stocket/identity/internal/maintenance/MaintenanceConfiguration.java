package com.stocket.identity.internal.maintenance;

import java.io.PrintStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for local maintenance commands.
 * Only activates when a maintenance parameter is provided.
 */
@Configuration
class MaintenanceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceConfiguration.class);

    /**
     * Creates an ApplicationRunner that executes the admin recovery command
     * when the {@code --stocket.maintenance.reset-admin} property is present.
     *
     * <p>The temporary password is printed to stdout AFTER the AFTER_COMMIT
     * audit listener has persisted the audit event, ensuring the audit trail
     * is complete before the password is revealed.
     *
     * @param command the admin recovery command
     * @return an ApplicationRunner that executes the command
     */
    @Bean
    ApplicationRunner adminRecoveryRunner(AdminRecoveryCommand command) {
        return args -> {
            if (args.containsOption("stocket.maintenance.reset-admin")) {
                String username = args.getOptionValues("stocket.maintenance.reset-admin").getFirst();
                log.info("Starting admin recovery for user: {}", username);
                String tempPassword = command.resetAdmin(username);
                // Print to stdout after AFTER_COMMIT audit listener has completed
                PrintStream out = System.out;
                out.println("Admin recovery successful.");
                out.println("Username: " + username);
                out.println("Temporary password: " + tempPassword);
                out.println("The user must change this password on first login.");
            }
        };
    }
}
