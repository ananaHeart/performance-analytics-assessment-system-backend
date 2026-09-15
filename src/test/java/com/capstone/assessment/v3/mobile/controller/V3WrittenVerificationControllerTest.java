package com.capstone.assessment.v3.mobile.controller;

import com.capstone.assessment.v3.auth.exception.V3AuthExceptionHandler;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.*;
import com.capstone.assessment.v3.mobile.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

class V3WrittenVerificationControllerTest {
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    final V3TeacherVerificationService objective=mock(V3TeacherVerificationService.class);
    final V3WrittenVerificationService written=mock(V3WrittenVerificationService.class);
    final V3AuthenticatedUser user=new V3AuthenticatedUser(42,"S1","","teacher","active","");
    MockMvc mvc;
    @BeforeEach void setup(){
        mvc=MockMvcBuilders.standaloneSetup(new V3TeacherVerificationController(objective,mapper,written)).setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).setControllerAdvice(new V3AuthExceptionHandler()).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));
        when(written.verify(eq(user),any())).thenAnswer(c->{V3WrittenVerificationBatch r=c.getArgument(1);return new V3VerificationResponse(r.syncUuid(),r.operationUuid(),"success",List.of(new V3VerificationResponse.Outcome(r.items().get(0).resultUuid(),"success","created",3L,List.of(),null)));});
    }
    @AfterEach void close(){SecurityContextHolder.clearContext();}
    ObjectNode request(String kind)throws Exception{
        var n=(ObjectNode)mapper.readTree(Files.readString(Path.of("docs/contracts/mobile-v3/1.0.0/fixtures/proposed/verification-"+kind+".json")));n.put("contractVersion","3.1");
        var item=(ObjectNode)n.path("items").get(0);item.put("testVersionNumber",1);item.put("evaluationReferenceHash","a".repeat(64));return n;
    }
    void reject(ObjectNode n)throws Exception{mvc.perform(post("/api/v3/mobile/verification-batches").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(n))).andExpect(status().isUnprocessableEntity());verifyNoInteractions(written,objective);}
    @Test void routesExplicitManualAndRubricVersionsToWrittenService()throws Exception{
        for(String kind:List.of("manual","rubric"))mvc.perform(post("/api/v3/mobile/verification-batches").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(request(kind))))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.data.syncStatus").value("success"));
        verify(written,times(2)).verify(eq(user),any());verifyNoInteractions(objective);
    }
    @Test void rejectsMissingReferenceBindingAndClientVerifier()throws Exception{
        var n=request("manual");((ObjectNode)n.path("items").get(0)).remove("evaluationReferenceHash");reject(n);
        n=request("manual");((ObjectNode)n.path("items").get(0).path("answers").get(0)).put("verifiedByUserId",42);reject(n);
    }
    @Test void rejectsMixedObjectiveKindAndUnknownManualFields()throws Exception{
        var n=request("manual");((ObjectNode)n.path("items").get(0).path("answers").get(0).path("evaluation")).put("kind","objective");reject(n);
        n=request("manual");((ObjectNode)n.path("items").get(0).path("answers").get(0).path("evaluation")).put("isCorrect",true);reject(n);
    }
    @Test void rejectsCoercedPointsAndFractionalReferenceVersion()throws Exception{
        var n=request("manual");((ObjectNode)n.path("items").get(0).path("answers").get(0).path("evaluation")).put("points","2.5");reject(n);
        n=request("manual");((ObjectNode)n.path("items").get(0)).put("testVersionNumber",1.5);reject(n);
    }
    @Test void rejectsUnknownRubricScoreFieldsAndNonUtcTimes()throws Exception{
        var n=request("rubric");((ObjectNode)n.path("items").get(0).path("answers").get(0).path("evaluation").path("criterionScores").get(0)).put("computedTotal",10);reject(n);
        n=request("manual");((ObjectNode)n.path("items").get(0).path("answers").get(0)).put("clientDecidedAt","2026-09-12T10:00:00+08:00");reject(n);
    }
}
