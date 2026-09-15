package com.capstone.assessment.v3.mobile.controller;

import com.capstone.assessment.v3.auth.exception.V3AuthExceptionHandler;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.*;
import com.capstone.assessment.v3.mobile.service.V3ResultCorrectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.*;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class V3ResultCorrectionControllerTest {
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    final V3ResultCorrectionService service=mock(V3ResultCorrectionService.class);
    final V3AuthenticatedUser teacher=new V3AuthenticatedUser(42,"S1","","teacher","active","");
    final String uuid="00000000-0000-4000-8000-000000000001",route="/api/v3/mobile/results/"+uuid+"/corrections";
    MockMvc mvc;
    @BeforeEach void setup(){
        mvc=MockMvcBuilders.standaloneSetup(new V3ResultCorrectionController(service,mapper)).setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).setControllerAdvice(new V3AuthExceptionHandler()).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(teacher,null,List.of()));
        when(service.correct(eq(teacher),eq(uuid),any())).thenReturn(null);
    }
    @AfterEach void close(){SecurityContextHolder.clearContext();}
    String request()throws Exception{return Files.readString(Path.of("docs/contracts/mobile-v3/1.10.0/fixtures/request.json"));}
    void reject(String json)throws Exception{mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isUnprocessableEntity());verifyNoInteractions(service);}
    @Test void acceptsCorrectionContractAndReturnsNoStore()throws Exception{
        mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content(request())).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"));
        verify(service).correct(eq(teacher),eq(uuid),any(V3CorrectionRequest.class));
    }
    @Test void rejectsClientActorUnknownAndMissingFields()throws Exception{
        var n=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(request());n.put("reopenedByUserId",42);reject(n.toString());
        n.remove("reopenedByUserId");n.remove("comment");reject(n.toString());
    }
    @Test void rejectsCoercionFractionalVersionDuplicateAndTrailingJson()throws Exception{
        var n=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(request());n.put("expectedRevision","4");reject(n.toString());
        n.put("expectedRevision",4.5);reject(n.toString());
        reject(request().replace("\"contractVersion\":", "\"contractVersion\":\"3.0\",\"contractVersion\":"));reject(request()+"{}");
    }
    @Test void rejectsNonUtcAndObjectiveClientScores()throws Exception{
        var n=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(request());
        var a=(com.fasterxml.jackson.databind.node.ObjectNode)n.withArray("answers").get(0);
        a.put("clientDecidedAt","2026-09-12T08:00:00+08:00");reject(n.toString());
        a.put("clientDecidedAt","2026-09-12T00:00:00Z");
        ((com.fasterxml.jackson.databind.node.ObjectNode)a.get("evaluation")).put("points",5);reject(n.toString());
    }
    @Test void rejectsOversizedBody()throws Exception{
        mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content(" ".repeat(V3ResultCorrectionController.MAX_BODY_BYTES+1))).andExpect(status().isPayloadTooLarge());verifyNoInteractions(service);
    }
    @Test void rejectsAnonymousAndNonTeacherBeforeBodyParsing()throws Exception{
        SecurityContextHolder.clearContext();mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content(request())).andExpect(status().isUnauthorized());
        var principal=new V3AuthenticatedUser(42,"S1","","principal","active","");SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of()));
        mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content(request())).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
}

