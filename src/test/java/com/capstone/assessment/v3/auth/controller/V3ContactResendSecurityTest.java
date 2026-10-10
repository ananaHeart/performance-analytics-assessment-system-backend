package com.capstone.assessment.v3.auth.controller;

import com.capstone.assessment.v3.auth.dto.V3RegistrationResponse;
import com.capstone.assessment.v3.auth.dto.V3ResendVerificationRequest;
import com.capstone.assessment.v3.auth.security.V3AccessDeniedHandler;
import com.capstone.assessment.v3.auth.security.V3AuthenticationEntryPoint;
import com.capstone.assessment.v3.auth.security.V3AuthenticationFilter;
import com.capstone.assessment.v3.auth.service.V3AuthService;
import com.capstone.assessment.v3.auth.service.V3TeacherRegistrationService;
import com.capstone.assessment.v3.system.config.V3SystemSecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** A registering teacher has no login yet, so the SMS resend must pass the real V3 security chain. */
@SpringJUnitWebConfig(V3ContactResendSecurityTest.Config.class)
@ActiveProfiles("v3")
class V3ContactResendSecurityTest {

    @Test
    void anonymousSmsResendReachesTheController(WebApplicationContext context) throws Exception {
        when(context.getBean(V3TeacherRegistrationService.class)
                .resendContact(any(V3ResendVerificationRequest.class), any()))
                .thenReturn(new V3RegistrationResponse.ContactVerification(
                        true, "+63 9** *** 4567", "sent", Instant.EPOCH, Instant.EPOCH));
        var mvc = webAppContextSetup(context).apply(springSecurity()).build();

        mvc.perform(post("/api/v3/auth/resend-teacher-contact-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeUuid\":\"00000000-0000-4000-8000-000000000001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactMasked").value("+63 9** *** 4567"));
    }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import({V3SystemSecurityConfig.class, V3AuthController.class})
    static class Config {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean V3AuthenticationEntryPoint entryPoint(ObjectMapper mapper) { return new V3AuthenticationEntryPoint(mapper); }
        @Bean V3AccessDeniedHandler accessDeniedHandler(ObjectMapper mapper) { return new V3AccessDeniedHandler(mapper); }
        @Bean V3AuthService authService() { return mock(V3AuthService.class); }
        @Bean V3TeacherRegistrationService registrationService() { return mock(V3TeacherRegistrationService.class); }
        @Bean V3AuthenticationFilter filter(V3AuthService service, V3AuthenticationEntryPoint entryPoint) {
            return new V3AuthenticationFilter(service, entryPoint);
        }
    }
}
