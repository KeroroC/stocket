package com.familyassets.system;

import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
class SystemController {

    private final String version;

    SystemController(BuildProperties buildProperties) {
        this.version = buildProperties.getVersion();
    }

    @GetMapping
    SystemStatus status() {
        return new SystemStatus("stocket", version);
    }
}
