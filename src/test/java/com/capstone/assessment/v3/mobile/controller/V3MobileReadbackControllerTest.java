package com.capstone.assessment.v3.mobile.controller;

import com.capstone.assessment.v3.auth.exception.*;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.security.*;
import com.capstone.assessment.v3.auth.service.V3AuthService;
import com.capstone.assessment.v3.mobile.dto.V3MobileReadback.*;
import com.capstone.assessment.v3.mobile.service.V3MobileReadbackService;
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
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringJUnitWebConfig(V3MobileReadbackControllerTest.Config.class)
@ActiveProfiles("v3")
class V3MobileReadbackControllerTest {
    static final String UUID="00000000-0000-4000-8000-000000000001";
    static final String GATED="00000000-0000-4000-8000-000000000002";
    static List<String> routes(String uuid) { return List.of("/api/v3/mobile/results/"+uuid,"/api/v3/mobile/results/"+uuid+"/analytics","/api/v3/mobile/syncs/"+uuid); }
    @Test void teacherCanReadAllThreeHandlersAndResponsesAreNotCached(WebApplicationContext context) throws Exception {
        var mvc=webAppContextSetup(context).apply(springSecurity()).build();
        for(String route:routes(UUID)) mvc.perform(get(route).header("Authorization","Bearer teacher"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.contractVersion").value("3.0"));
    }
    @Test void missingExpiredAndWrongRoleCannotReadAnyHandler(WebApplicationContext context) throws Exception {
        var mvc=webAppContextSetup(context).apply(springSecurity()).build();
        for(String route:routes(UUID)) {
            mvc.perform(get(route)).andExpect(status().isUnauthorized());
            mvc.perform(get(route).header("Authorization","Bearer expired")).andExpect(status().isUnauthorized());
            mvc.perform(get(route).header("Authorization","Bearer principal")).andExpect(status().isForbidden());
        }
    }
    @Test void releaseGateReturnsExplicitUnavailableCode(WebApplicationContext context) throws Exception {
        var mvc=webAppContextSetup(context).apply(springSecurity()).build();
        for(String route:routes(GATED)) mvc.perform(get(route).header("Authorization","Bearer teacher"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.errors.code").value("MOBILE_READBACK_UNAVAILABLE"));
    }
    @Test void readbackDoesNotUnlockMobileWrites(WebApplicationContext context) throws Exception {
        var mvc=webAppContextSetup(context).apply(springSecurity()).build();
        for(String route:List.of("/api/v3/mobile/scan-pages","/api/v3/mobile/scan-pages/"+UUID+"/detections","/api/v3/mobile/verification-batches"))
            mvc.perform(post(route).header("Authorization","Bearer teacher")).andExpect(status().isForbidden());
    }
    @Configuration @EnableWebSecurity @EnableWebMvc
    @Import({V3SystemSecurityConfig.class,V3MobileReadbackController.class,V3AuthExceptionHandler.class})
    static class Config {
        @Bean ObjectMapper mapper() {return new ObjectMapper().findAndRegisterModules();}
        @Bean V3AuthenticationEntryPoint entry(ObjectMapper mapper) {return new V3AuthenticationEntryPoint(mapper);}
        @Bean V3AccessDeniedHandler denied(ObjectMapper mapper) {return new V3AccessDeniedHandler(mapper);}
        @Bean V3AuthService auth() {
            var auth=mock(V3AuthService.class);
            when(auth.authenticate("teacher")).thenReturn(Optional.of(new V3AuthenticatedUser(42,"S1","","teacher","active","")));
            when(auth.authenticate("principal")).thenReturn(Optional.of(new V3AuthenticatedUser(43,"S1","","principal","active","")));
            return auth;
        }
        @Bean V3AuthenticationFilter filter(V3AuthService auth,V3AuthenticationEntryPoint entry) {return new V3AuthenticationFilter(auth,entry);}
        @Bean V3MobileReadbackService service(ObjectMapper mapper) throws Exception {
            var service=mock(V3MobileReadbackService.class);
            var folder=Path.of("docs/contracts/mobile-v3/1.0.0/fixtures/proposed");
            when(service.result(any(),eq(UUID))).thenReturn(mapper.treeToValue(mapper.readTree(folder.resolve("result-finalized.json").toFile()).get("data"),Result.class));
            when(service.analytics(any(),eq(UUID))).thenReturn(mapper.treeToValue(mapper.readTree(folder.resolve("analytics-ready-score-only.json").toFile()).get("data"),Analytics.class));
            when(service.sync(any(),eq(UUID))).thenReturn(mapper.treeToValue(mapper.readTree(folder.resolve("sync-page-partial.json").toFile()).get("data"),Sync.class));
            var error=new V3AuthException("MOBILE_READBACK_UNAVAILABLE","Readback is disabled.",HttpStatus.SERVICE_UNAVAILABLE);
            when(service.result(any(),eq(GATED))).thenThrow(error);when(service.analytics(any(),eq(GATED))).thenThrow(error);when(service.sync(any(),eq(GATED))).thenThrow(error);
            return service;
        }
    }
}
