package com.capstone.assessment.v3.mobile.controller;

import com.capstone.assessment.v3.auth.exception.*;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.security.*;
import com.capstone.assessment.v3.auth.service.V3AuthService;
import com.capstone.assessment.v3.mobile.dto.V3EvaluationReference;
import com.capstone.assessment.v3.mobile.service.V3EvaluationReferenceService;
import com.capstone.assessment.v3.system.config.V3SystemSecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.nio.file.Path;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringJUnitWebConfig(V3EvaluationReferenceControllerTest.Config.class)
@ActiveProfiles("v3")
class V3EvaluationReferenceControllerTest {
    static final String UUID="00000000-0000-4000-8000-000000000001";
    static String route(String uuid){return "/api/v3/mobile/test-assignments/"+uuid+"/evaluation-reference";}
    @Test void activeTeacherReceivesTypedReferenceWithoutCaching(WebApplicationContext context)throws Exception {
        webAppContextSetup(context).apply(springSecurity()).build().perform(get(route(UUID)).header("Authorization","Bearer teacher"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.data.contractVersion").value("3.0")).andExpect(jsonPath("$.data.rubrics[0].criteria[0].isRequired").value(true))
                .andExpect(jsonPath("$.data.answerKeys").doesNotExist());
    }
    @Test void anonymousExpiredAndPrincipalSessionsCannotReadReference(WebApplicationContext context)throws Exception {
        var mvc=webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get(route(UUID))).andExpect(status().isUnauthorized());
        mvc.perform(get(route(UUID)).header("Authorization","Bearer expired")).andExpect(status().isUnauthorized());
        mvc.perform(get(route(UUID)).header("Authorization","Bearer principal")).andExpect(status().isForbidden());
    }
    @Test void referenceReleaseGateAndExistingAttachmentWriteGateRemainClosed(WebApplicationContext context)throws Exception {
        var mvc=webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get(route("00000000-0000-4000-8000-000000000002")).header("Authorization","Bearer teacher"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.errors.code").value("EVALUATION_REFERENCE_UNAVAILABLE"));
        mvc.perform(post("/api/v3/mobile/attachments").header("Authorization","Bearer teacher")).andExpect(status().isForbidden());
    }
    @Configuration @EnableWebSecurity @EnableWebMvc
    @Import({V3SystemSecurityConfig.class,V3EvaluationReferenceController.class,V3AuthExceptionHandler.class})
    static class Config {
        @Bean ObjectMapper mapper(){return new ObjectMapper().findAndRegisterModules();}
        @Bean V3AuthenticationEntryPoint entry(ObjectMapper m){return new V3AuthenticationEntryPoint(m);}
        @Bean V3AccessDeniedHandler denied(ObjectMapper m){return new V3AccessDeniedHandler(m);}
        @Bean V3AuthService auth(){var a=mock(V3AuthService.class);
            when(a.authenticate("teacher")).thenReturn(Optional.of(new V3AuthenticatedUser(42,"S1","","teacher","active","")));
            when(a.authenticate("principal")).thenReturn(Optional.of(new V3AuthenticatedUser(43,"S1","","principal","active","")));return a;}
        @Bean V3AuthenticationFilter filter(V3AuthService a,V3AuthenticationEntryPoint e){return new V3AuthenticationFilter(a,e);}
        @Bean V3EvaluationReferenceService service(ObjectMapper m)throws Exception {
            var s=mock(V3EvaluationReferenceService.class);
            when(s.get(any(),eq(UUID))).thenReturn(m.treeToValue(m.readTree(Path.of("docs/contracts/mobile-v3/1.0.0/fixtures/proposed/evaluation-reference.json").toFile()).get("data"),V3EvaluationReference.class));
            when(s.get(any(),eq("00000000-0000-4000-8000-000000000002"))).thenThrow(new V3AuthException("EVALUATION_REFERENCE_UNAVAILABLE","Reference unavailable.",HttpStatus.SERVICE_UNAVAILABLE));return s;
        }
    }
}
