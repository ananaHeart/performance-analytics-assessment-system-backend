package com.capstone.assessment.v3.mobile.controller;

import com.capstone.assessment.v3.auth.exception.V3AuthExceptionHandler;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.*;
import com.capstone.assessment.v3.mobile.service.V3ResultSupersedeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class V3ResultSupersedeControllerTest {
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    final V3ResultSupersedeService service=mock(V3ResultSupersedeService.class);
    final V3AuthenticatedUser teacher=new V3AuthenticatedUser(42,"S1","","teacher","active","");
    final String uuid="00000000-0000-4000-8000-000000000001",route="/api/v3/mobile/results/"+uuid+"/supersede";
    MockMvc mvc;
    @BeforeEach void setup(){
        mvc=MockMvcBuilders.standaloneSetup(new V3ResultSupersedeController(service,mapper)).setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).setControllerAdvice(new V3AuthExceptionHandler()).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(teacher,null,List.of()));
        when(service.supersede(eq(teacher),eq(uuid),any())).thenReturn(new V3LifecycleAck(uuid,"superseded",5,1,"00000000-0000-4000-8000-000000000002","created",Instant.parse("2026-09-12T00:00:00Z")));
    }
    @AfterEach void close(){SecurityContextHolder.clearContext();}
    String request()throws Exception{return Files.readString(Path.of("docs/contracts/mobile-v3/1.0.0/fixtures/proposed/supersede-request.json"));}
    void reject(String json)throws Exception{mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isUnprocessableEntity());verifyNoInteractions(service);}
    @Test void acceptsExistingContractAndReturnsNoStore()throws Exception{
        mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content(request())).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.data.resultStatus").value("superseded"));
        verify(service).supersede(eq(teacher),eq(uuid),any(V3SupersedeRequest.class));
    }
    @Test void rejectsClientActorUnknownAndMissingFields()throws Exception{
        var n=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(request());n.put("supersedeedByUserId",42);reject(n.toString());
        n.remove("supersedeedByUserId");n.remove("comment");reject(n.toString());
    }
    @Test void rejectsCoercionFractionalVersionDuplicateAndTrailingJson()throws Exception{
        var n=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(request());n.put("expectedRevision","4");reject(n.toString());
        n.put("expectedRevision",4.5);reject(n.toString());
        reject(request().replace("\"contractVersion\":", "\"contractVersion\":\"3.0\",\"contractVersion\":"));reject(request()+"{}");
    }
    @Test void returnsDurableLinkWithNoStore()throws Exception{
        when(service.supersession(teacher,uuid)).thenReturn(new V3LifecycleAck(uuid,"superseded",5,1,"00000000-0000-4000-8000-000000000002","created",Instant.parse("2026-09-12T00:00:00Z")));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v3/mobile/results/"+uuid+"/supersession")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.data.resultStatus").value("superseded"));
    }
    @Test void rejectsOversizedBody()throws Exception{
        mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content(" ".repeat(V3ResultSupersedeController.MAX_BODY_BYTES+1))).andExpect(status().isPayloadTooLarge());verifyNoInteractions(service);
    }
    @Test void rejectsAnonymousAndNonTeacherBeforeBodyParsing()throws Exception{
        SecurityContextHolder.clearContext();mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content(request())).andExpect(status().isUnauthorized());
        var principal=new V3AuthenticatedUser(42,"S1","","principal","active","");SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of()));
        mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content(request())).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
}
