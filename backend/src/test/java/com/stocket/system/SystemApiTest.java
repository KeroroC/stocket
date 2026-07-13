package com.stocket.system;

import java.util.Properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = SystemController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.stocket\\.identity\\.internal\\.security\\.(SessionAuthenticationFilter|PasswordChangeRequiredFilter)"))
@AutoConfigureMockMvc(addFilters = false)
@Import({SystemApiTest.BuildInfoConfiguration.class,
        SystemApiTest.ValidationProbeController.class,
        ApiExceptionHandler.class})
class SystemApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsStableBuildInformation() throws Exception {
        mockMvc.perform(get("/api/v1/system"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("stocket"))
                .andExpect(jsonPath("$.version").isString());
    }

    @Test
    void returnsProblemDetailsForValidationErrors() throws Exception {
        mockMvc.perform(post("/api/v1/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("value"));
    }

    @RestController
    @RequestMapping("/api/v1/test")
    static class ValidationProbeController {

        @PostMapping("/validation")
        void validate(@Valid @RequestBody ValidationProbe probe) {
        }
    }

    record ValidationProbe(@NotBlank String value) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class BuildInfoConfiguration {

        @Bean
        BuildProperties buildProperties() {
            Properties properties = new Properties();
            properties.setProperty("version", "test");
            return new BuildProperties(properties);
        }
    }
}
