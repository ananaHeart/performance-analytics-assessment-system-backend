package com.capstone.assessment.v3.mobile.controller;

import com.capstone.assessment.v3.auth.exception.V3AuthExceptionHandler;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.*;
import com.capstone.assessment.v3.mobile.service.V3DetectionUploadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Handler parsing checks; production security remains denied separately. */
class V3DetectionUploadControllerTest {
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    private final V3DetectionUploadService service=mock(V3DetectionUploadService.class);
    private final V3AuthenticatedUser user=new V3AuthenticatedUser(42,"S1","","teacher","active","");
    private final String route="/api/v3/mobile/scan-pages/00000000-0000-4000-8000-000000000001/detections";
    private MockMvc mvc;
    private String valid;
    @BeforeEach void setup() throws Exception {
        valid=Files.readString(Path.of("docs/contracts/mobile-v3/1.0.0/fixtures/proposed/detections-mc.json"));
        mvc=MockMvcBuilders.standaloneSetup(new V3DetectionUploadController(service,mapper))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new V3AuthExceptionHandler()).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));
        when(service.upload(eq(user),anyString(),any())).thenAnswer(call -> { V3DetectionBatch batch=call.getArgument(2);
            return new V3DetectionUploadResponse(batch.syncUuid(),batch.operationUuid(),"created",2,
                    List.of(new V3DetectionUploadResponse.IdMapping("omr_detection",batch.detections().get(0).detectionUuid(),77)),Instant.now()); });
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    @ParameterizedTest @ValueSource(strings={"detections-mc","detections-tf","detections-uncertain"})
    void acceptsFrozenProposedFixtureShape(String fixture) throws Exception {
        mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content(Files.readString(Path.of("docs/contracts/mobile-v3/1.0.0/fixtures/proposed/"+fixture+".json"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.disposition").value("created"))
                .andExpect(jsonPath("$.data.idMappings[0].centralId").value(77));
    }
    @Test void replayUses200() throws Exception {
        when(service.upload(eq(user),anyString(),any())).thenReturn(new V3DetectionUploadResponse("s","o","replayed",2,List.of(),Instant.now()));
        mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isOk());
    }
    @ParameterizedTest @ValueSource(strings={"pointsEarned","isCorrect","comment","centralQuestionId","localImagePath"})
    void rejectsUnknownObservationFields(String field) throws Exception {
        var json=(ObjectNode)mapper.readTree(valid); ((ObjectNode)json.get("detections").get(0)).put(field,"forbidden");
        reject(mapper.writeValueAsString(json));
    }
    @Test void rejectsUnknownRootField() throws Exception { reject(valid.replace("\"contractVersion\"","\"score\": 1, \"contractVersion\"")); }
    @Test void rejectsDuplicateKeysTrailingJsonAndStringNumbers() throws Exception {
        reject(valid.replace("\"confidence\": 0.98","\"confidence\": 0.98, \"confidence\": 0.5"));
        reject(valid+" {}"); reject(valid.replace("0.98","\"0.98\""));
    }
    @Test void requiresExplicitNullableOptionField() throws Exception {
        var json=(ObjectNode)mapper.readTree(valid); ((ObjectNode)json.get("detections").get(0)).remove("detectedOption"); reject(mapper.writeValueAsString(json));
    }
    @Test void boundsActualJsonBytesWhenLengthIsUnknown() throws Exception {
        var request = new org.springframework.mock.web.MockHttpServletRequest() {
            @Override public long getContentLengthLong() { return -1; }
        };
        request.setContent(" ".repeat(V3DetectionUploadController.MAX_BODY_BYTES+1).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var error = org.junit.jupiter.api.Assertions.assertThrows(com.capstone.assessment.v3.auth.exception.V3AuthException.class,
                () -> new V3DetectionUploadController(service,mapper).upload(user,"00000000-0000-4000-8000-000000000001",request));
        org.junit.jupiter.api.Assertions.assertEquals("PAYLOAD_TOO_LARGE",error.getCode());
        verifyNoInteractions(service);
    }
    @Test void rejectsWrongMediaType() throws Exception { mvc.perform(post(route).contentType(MediaType.TEXT_PLAIN).content(valid)).andExpect(status().isUnsupportedMediaType()); }
    @Test void rejectsMissingTeacherBeforeServiceCall() throws Exception { SecurityContextHolder.clearContext();
        mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isForbidden());verifyNoInteractions(service); }
    private void reject(String body) throws Exception {
        mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnprocessableEntity());verifyNoInteractions(service);
    }
}
