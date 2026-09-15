package com.capstone.assessment.v3.mobile.controller;

import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.security.V3AccessDeniedHandler;
import com.capstone.assessment.v3.auth.security.V3AuthenticationEntryPoint;
import com.capstone.assessment.v3.auth.security.V3AuthenticationFilter;
import com.capstone.assessment.v3.auth.service.V3AuthService;
import com.capstone.assessment.v3.system.config.V3SystemSecurityConfig;
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

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringJUnitWebConfig(V3ScanPageSecurityGateTest.Config.class)
@ActiveProfiles("v3")
class V3ScanPageSecurityGateTest {
    @Test void actualV3SecurityDeniesSupersession(WebApplicationContext context)throws Exception{
        var mvc=webAppContextSetup(context).apply(springSecurity()).build();String base="/api/v3/mobile/results/00000000-0000-4000-8000-000000000001/";
        mvc.perform(post(base+"supersede").header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
        mvc.perform(post(base+"supersede")).andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(base+"supersession").header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
    }
    @Test void actualV3SecurityDeniesResultCorrections(WebApplicationContext context) throws Exception {
        var mvc=webAppContextSetup(context).apply(springSecurity()).build();
        String route="/api/v3/mobile/results/00000000-0000-4000-8000-000000000001/corrections";
        mvc.perform(post(route).header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
        mvc.perform(post(route)).andExpect(status().isUnauthorized());
    }
    @Test void actualV3SecurityDeniesResultReopen(WebApplicationContext context) throws Exception {
        var mvc=webAppContextSetup(context).apply(springSecurity()).build();
        String route="/api/v3/mobile/results/00000000-0000-4000-8000-000000000001/reopen";
        mvc.perform(post(route).header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
        mvc.perform(post(route)).andExpect(status().isUnauthorized());
    }
    @Test void actualV3SecurityDeniesUploadEvenWithValidTeacherSession(WebApplicationContext context) throws Exception {
        var mvc = webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(post("/api/v3/mobile/scan-pages").header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v3/scoring/results/100/finalize").header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v3/mobile/scan-pages")).andExpect(status().isUnauthorized());
    }

    @Test void actualV3SecurityDeniesDetections(WebApplicationContext context) throws Exception {
        var mvc = webAppContextSetup(context).apply(springSecurity()).build();
        String route = "/api/v3/mobile/scan-pages/00000000-0000-4000-8000-000000000001/detections";
        mvc.perform(post(route).header("Authorization", "Bearer test-token")).andExpect(status().isForbidden());
        mvc.perform(post(route)).andExpect(status().isUnauthorized());
    }

    @Test void actualV3SecurityDeniesTeacherVerification(WebApplicationContext context) throws Exception {
        var mvc = webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(post("/api/v3/mobile/verification-batches").header("Authorization", "Bearer test-token")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v3/mobile/verification-batches")).andExpect(status().isUnauthorized());
    }

    @Test void actualV3SecurityDeniesAttachments(WebApplicationContext context) throws Exception {
        var mvc=webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(post("/api/v3/mobile/attachments").header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v3/mobile/attachments")).andExpect(status().isUnauthorized());
    }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import(V3SystemSecurityConfig.class)
    static class Config {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean V3AuthenticationEntryPoint entryPoint(ObjectMapper mapper) { return new V3AuthenticationEntryPoint(mapper); }
        @Bean V3AccessDeniedHandler accessDeniedHandler(ObjectMapper mapper) { return new V3AccessDeniedHandler(mapper); }
        @Bean V3AuthService authService() {
            var service = mock(V3AuthService.class);
            when(service.authenticate("test-token")).thenReturn(Optional.of(
                    new V3AuthenticatedUser(42, "S1", "teacher@test", "teacher", "active", "session")));
            return service;
        }
        @Bean V3AuthenticationFilter filter(V3AuthService service, V3AuthenticationEntryPoint entryPoint) {
            return new V3AuthenticationFilter(service, entryPoint);
        }
    }
}
