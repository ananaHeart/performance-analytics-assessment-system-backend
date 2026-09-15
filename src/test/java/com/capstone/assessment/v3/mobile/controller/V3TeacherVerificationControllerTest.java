package com.capstone.assessment.v3.mobile.controller;

import com.capstone.assessment.v3.auth.exception.*;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.*;
import com.capstone.assessment.v3.mobile.service.V3TeacherVerificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class V3TeacherVerificationControllerTest {
    static final String ROUTE="/api/v3/mobile/verification-batches";
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    final V3TeacherVerificationService service=mock(V3TeacherVerificationService.class);
    final V3AuthenticatedUser user=new V3AuthenticatedUser(42,"S1","","teacher","active","");
    MockMvc mvc;String valid;
    @BeforeEach void setup() throws Exception {
        valid=fixture("verification-objective");
        mvc=MockMvcBuilders.standaloneSetup(new V3TeacherVerificationController(service,mapper,mock(com.capstone.assessment.v3.mobile.service.V3WrittenVerificationService.class)))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).setControllerAdvice(new V3AuthExceptionHandler()).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));
        when(service.verify(eq(user),any())).thenAnswer(call->{V3VerificationBatch batch=call.getArgument(1);
            var item=new V3VerificationResponse.Outcome(batch.items().get(0).resultUuid(),"failed","rejected",null,List.of(),
                    new V3VerificationResponse.Error("DEPENDENCY_NOT_READY","Commit the detection first.",true));
            return new V3VerificationResponse(batch.syncUuid(),batch.operationUuid(),"failed",List.of(item));});
    }
    @AfterEach void close(){SecurityContextHolder.clearContext();}
    String fixture(String name) throws Exception{return Files.readString(Path.of("docs/contracts/mobile-v3/1.0.0/fixtures/proposed/"+name+".json"));}
    void reject(String body) throws Exception {mvc.perform(post(ROUTE).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnprocessableEntity());verifyNoInteractions(service);}
    @ParameterizedTest @ValueSource(strings={"verification-objective","verification-rescan","verification-partial-request"})
    void processedBatchUses200AndRequiresInspectingEachItem(String name) throws Exception {
        mvc.perform(post(ROUTE).contentType(MediaType.APPLICATION_JSON).content(fixture(name)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.syncStatus").value("failed")).andExpect(jsonPath("$.data.items[0].status").value("failed"));
    }
    @ParameterizedTest @ValueSource(strings={"verification-manual","verification-rubric"})
    void writtenScoringIsNotAcceptedByThisSlice(String name) throws Exception {reject(fixture(name));}
    @ParameterizedTest @ValueSource(strings={"pointsEarned","isCorrect","verifiedByUserId","finalizedAt","selectedOption"})
    void rejectsClientScoreAndVerifierFields(String field) throws Exception {
        ObjectNode tree=(ObjectNode)mapper.readTree(valid);((ObjectNode)tree.get("items").get(0).get("answers").get(0)).put(field,"forbidden");reject(mapper.writeValueAsString(tree));
    }
    @Test void rejectsUnknownRootField() throws Exception {reject(valid.replace("\"contractVersion\"","\"totalScore\":2,\"contractVersion\""));}
    @Test void rejectsDuplicateKeysTrailingJsonAndFractionalRevision() throws Exception {
        reject(valid.replace("\"expectedRevision\": 2","\"expectedRevision\": 2, \"expectedRevision\": 3"));
        reject(valid+" {}");reject(valid.replace("\"expectedRevision\": 2","\"expectedRevision\": 2.5"));
        reject(valid.replace("\"expectedRevision\": 2","\"expectedRevision\": \"2\""));
    }
    @Test void clientTimeMustBeExplicitUtcText() throws Exception {
        reject(valid.replace("\"2026-09-12T02:00:00Z\"","12345"));
        reject(valid.replace("2026-09-12T02:00:00Z","2026-09-12T10:00:00+08:00"));
    }
    @Test void missingNullableFieldIsRejected() throws Exception {
        ObjectNode tree=(ObjectNode)mapper.readTree(valid);((ObjectNode)tree.get("items").get(0).get("pageDecisions").get(0)).remove("reasonCode");reject(mapper.writeValueAsString(tree));
    }
    @Test void enforcesActualBodyBoundWithoutContentLength() {
        var request=new MockHttpServletRequest(){@Override public long getContentLengthLong(){return -1;}};
        request.setContent(" ".repeat(V3TeacherVerificationController.MAX_BODY_BYTES+1).getBytes(StandardCharsets.UTF_8));
        var error=assertThrows(V3AuthException.class,()->new V3TeacherVerificationController(service,mapper,mock(com.capstone.assessment.v3.mobile.service.V3WrittenVerificationService.class)).verify(user,request));
        assertEquals("PAYLOAD_TOO_LARGE",error.getCode());verifyNoInteractions(service);
    }
    @Test void rejectsAnonymousBeforeParsing() throws Exception {
        SecurityContextHolder.clearContext();mvc.perform(post(ROUTE).contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
}
