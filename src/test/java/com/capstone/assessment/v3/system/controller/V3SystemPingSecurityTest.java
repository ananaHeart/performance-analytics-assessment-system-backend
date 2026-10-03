package com.capstone.assessment.v3.system.controller;

import com.capstone.assessment.v3.auth.security.V3AccessDeniedHandler;
import com.capstone.assessment.v3.auth.security.V3AuthenticationEntryPoint;
import com.capstone.assessment.v3.auth.security.V3AuthenticationFilter;
import com.capstone.assessment.v3.auth.service.V3AuthService;
import com.capstone.assessment.v3.system.config.V3SystemSecurityConfig;
import com.capstone.assessment.v3.system.service.V3DatabaseReadinessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** Through the real V3 security chain and authentication filter, as the external pinger calls it. */
@SpringJUnitWebConfig(V3SystemPingSecurityTest.Config.class)
@ActiveProfiles("v3")
class V3SystemPingSecurityTest {

    @Test
    void anonymousPingIsAnsweredWithoutTouchingTheDatabase(WebApplicationContext context) throws Exception {
        var mvc = webAppContextSetup(context).apply(springSecurity()).build();

        mvc.perform(get("/api/v3/system/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("up"));
        verifyNoInteractions(context.getBean(V3DatabaseReadinessService.class));
    }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import({V3SystemSecurityConfig.class, V3SystemController.class})
    static class Config {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean V3AuthenticationEntryPoint entryPoint(ObjectMapper mapper) { return new V3AuthenticationEntryPoint(mapper); }
        @Bean V3AccessDeniedHandler accessDeniedHandler(ObjectMapper mapper) { return new V3AccessDeniedHandler(mapper); }
        @Bean V3AuthService authService() { return mock(V3AuthService.class); }
        @Bean V3DatabaseReadinessService readinessService() { return mock(V3DatabaseReadinessService.class); }
        @Bean V3AuthenticationFilter filter(V3AuthService service, V3AuthenticationEntryPoint entryPoint) {
            return new V3AuthenticationFilter(service, entryPoint);
        }
    }
}
