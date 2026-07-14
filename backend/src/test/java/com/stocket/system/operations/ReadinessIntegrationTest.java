package com.stocket.system.operations;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "STOCKET_MASTER_KEY=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "spring.datasource.hikari.connection-timeout=500",
        "management.endpoint.health.probes.add-additional-paths=true"
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DisabledInAotMode
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class ReadinessIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");
    static final Path attachmentDirectory = temporaryDirectory();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("stocket.attachment.directory", () -> attachmentDirectory.toString());
    }

    @Autowired MockMvc mockMvc;

    @Test void readinessTracksStorageAndDatabaseWhileLivenessStaysUp() throws Exception {
        mockMvc.perform(get("/readyz")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/livez")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));

        Path unavailable = attachmentDirectory.resolveSibling(attachmentDirectory.getFileName() + "-offline");
        Files.move(attachmentDirectory, unavailable);
        try {
            mockMvc.perform(get("/readyz")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value("DOWN"));
            mockMvc.perform(get("/livez")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        } finally {
            Files.move(unavailable, attachmentDirectory);
        }

        postgres.stop();
        mockMvc.perform(get("/readyz")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value("DOWN"));
        mockMvc.perform(get("/livez")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    private static Path temporaryDirectory() {
        try { return Files.createTempDirectory("stocket-readiness-"); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
}
