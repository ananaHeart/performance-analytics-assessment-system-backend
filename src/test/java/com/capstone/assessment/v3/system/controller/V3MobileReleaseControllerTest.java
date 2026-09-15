package com.capstone.assessment.v3.system.controller;

import com.capstone.assessment.common.exception.GlobalExceptionHandler;
import com.capstone.assessment.v3.auth.config.V3AuthProperties;
import com.capstone.assessment.v3.auth.config.V3MfaProperties;
import com.capstone.assessment.v3.mobile.config.V3MobileReleaseProperties;
import com.capstone.assessment.v3.system.config.V3BaselineProperties;
import com.capstone.assessment.v3.system.dto.V3DatabaseReadinessResponse;
import com.capstone.assessment.v3.system.service.V3DatabaseReadinessService;
import com.capstone.assessment.v3.system.service.V3MobileReleaseReadinessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

class V3MobileReleaseControllerTest {

    @TempDir Path evidenceDirectory;

    private WebApplicationContextRunner runner(String... profiles) {
        return new WebApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles))
                .withUserConfiguration(Config.class);
    }

    @Test
    void normalV3ExposesReleaseStatusWithoutEnablingWrites() {
        runner("v3").run(context -> {
            var mvc = webAppContextSetup(context).build();
            mvc.perform(get("/api/v3/system/mobile-release-readiness"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.data.backendReady").value(false))
                    .andExpect(jsonPath("$.data.writeApiEnabled").value(false))
                    .andExpect(jsonPath("$.data.fullyConnected").value(false))
                    .andExpect(jsonPath("$.data.checks[0].check").value("release_profile"))
                    .andExpect(jsonPath("$.data.checks[0].passed").value(false));
        });
    }

    @Test
    void configuredReleaseStillReturnsReady() {
        runner("v3", "v3-mobile-release").withPropertyValues(
                "app.v3.mobile.release.profile-enabled=true",
                "app.v3.mobile.release.http-enabled=true",
                "app.v3.mobile.release.public-base-url=http://127.0.0.1:8080",
                "app.v3.mobile.release.adb-reverse-enabled=true",
                "app.v3.mobile.scan-recovery-enabled=true",
                "app.v3.mobile.finalization-enabled=true",
                "app.v3.mobile.readback-enabled=true",
                "app.v3.mobile.evaluation-reference-enabled=true",
                "app.v3.mobile.reopen-enabled=true",
                "app.v3.mobile.correction-enabled=true",
                "app.v3.mobile.supersede-enabled=true",
                "app.v3.baseline.expected-table-count=79",
                "app.v3.baseline.expected-foreign-key-count=200",
                "app.v3.baseline.expected-check-constraint-count=131",
                "app.v3.baseline.expected-unique-constraint-count=118",
                "app.v3.baseline.validate-on-startup=true",
                "app.v3.scan-evidence.storage-directory=" + evidenceDirectory.toAbsolutePath()
        ).run(context -> webAppContextSetup(context).build()
                .perform(get("/api/v3/system/mobile-release-readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.backendReady").value(true))
                .andExpect(jsonPath("$.data.writeApiEnabled").value(true))
                .andExpect(jsonPath("$.data.fullyConnected").value(false)));
    }

    @Test
    void missingResourceIsA404RatherThanAnUnhandled500() {
        runner("v3").run(context -> webAppContextSetup(context).build()
                .perform(get("/api/v3/system/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("The requested resource was not found.")));
    }

    @Configuration
    @EnableWebMvc
    @EnableConfigurationProperties({V3BaselineProperties.class, V3MobileReleaseProperties.class,
            V3AuthProperties.class, V3MfaProperties.class})
    @Import({V3MobileReleaseController.class, V3MobileReleaseReadinessService.class,
            GlobalExceptionHandler.class})
    static class Config implements WebMvcConfigurer {
        @Bean V3DatabaseReadinessService databaseReadinessService() {
            var database = mock(V3DatabaseReadinessService.class);
            when(database.checkReadiness()).thenReturn(new V3DatabaseReadinessResponse(
                    "v3", "synthetic", true, 79, 200, 131, 118, 5, 1, 4, List.of()));
            return database;
        }

        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            registry.addResourceHandler("/**").addResourceLocations("classpath:/static/");
        }
    }
}
