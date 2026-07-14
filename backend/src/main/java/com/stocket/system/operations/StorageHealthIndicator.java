package com.stocket.system.operations;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("attachmentStorage")
public class StorageHealthIndicator implements HealthIndicator {
    private final Path directory;

    public StorageHealthIndicator(@Value("${stocket.attachment.directory}") String directory) {
        this.directory = Path.of(directory);
    }

    @Override
    public Health health() {
        if (!Files.isDirectory(directory) || !Files.isWritable(directory)) {
            return Health.down().withDetail("reason", "attachment storage unavailable").build();
        }
        Path probe = null;
        try {
            probe = Files.createTempFile(directory, ".health-", ".probe");
            return Health.up().build();
        } catch (Exception error) {
            return Health.down().withDetail("reason", "attachment storage write failed").build();
        } finally {
            if (probe != null) try { Files.deleteIfExists(probe); } catch (Exception ignored) { }
        }
    }
}
