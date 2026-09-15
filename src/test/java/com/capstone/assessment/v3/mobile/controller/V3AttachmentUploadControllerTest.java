package com.capstone.assessment.v3.mobile.controller;

import com.capstone.assessment.v3.auth.exception.V3AuthExceptionHandler;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.*;
import com.capstone.assessment.v3.mobile.service.V3AttachmentUploadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockPart;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Handler tests bypass security deliberately; actual security denial is verified separately. */
class V3AttachmentUploadControllerTest {
    private static final String ROUTE="/api/v3/mobile/attachments";
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    private final V3AttachmentUploadService service=mock(V3AttachmentUploadService.class);
    private final V3AuthenticatedUser user=new V3AuthenticatedUser(42,"S1","","teacher","active","test");
    private MockMvc mvc;private byte[] valid;
    @BeforeEach void setup()throws Exception {
        valid=Files.readAllBytes(Path.of("docs/contracts/mobile-v3/1.0.0/fixtures/proposed/answer-crop-metadata.json"));
        mvc=MockMvcBuilders.standaloneSetup(new V3AttachmentUploadController(service,mapper))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).setControllerAdvice(new V3AuthExceptionHandler()).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));
        when(service.upload(eq(user),any(),any())).thenAnswer(call->{V3AttachmentMetadata r=call.getArgument(1);return new V3AttachmentResponse(r.attachmentUuid(),123,r.contentHash(),"created",Instant.now());});
    }
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    @Test void acceptsFrozenMultipartAndReturnsPrivateAcknowledgement()throws Exception {
        mvc.perform(multipart(ROUTE).part(metadata(valid),file())).andExpect(status().isCreated())
            .andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.data.backendAttachmentId").value(123))
            .andExpect(jsonPath("$.data.storageKey").doesNotExist());
    }
    @Test void replayReturns200()throws Exception {
        var r=mapper.readValue(valid,V3AttachmentMetadata.class);
        when(service.upload(eq(user),any(),any())).thenReturn(new V3AttachmentResponse(r.attachmentUuid(),123,r.contentHash(),"replayed",Instant.now()));
        mvc.perform(multipart(ROUTE).part(metadata(valid),file())).andExpect(status().isOk()).andExpect(jsonPath("$.data.uploadStatus").value("replayed"));
    }
    @Test void rejectsPathsScoresUnknownNestedFieldsAndMissingNullableFields()throws Exception {
        for(String field:List.of("localImagePath","points","backendAttachmentId")) {
            var n=(ObjectNode)mapper.readTree(valid);n.put(field,"forbidden");reject(mapper.writeValueAsBytes(n));
        }
        var n=(ObjectNode)mapper.readTree(valid);((ObjectNode)n.get("crop")).put("localPath","device");reject(mapper.writeValueAsBytes(n));
        n=(ObjectNode)mapper.readTree(valid);n.remove("sourceAttachmentUuid");reject(mapper.writeValueAsBytes(n));verifyNoInteractions(service);
    }
    @Test void rejectsCoercionDuplicateKeysTrailingJsonAndNonUtcTimestamp()throws Exception {
        for(Object value:List.of("1024",1.5)) {
            var n=(ObjectNode)mapper.readTree(valid);n.set("fileSizeBytes",mapper.valueToTree(value));reject(mapper.writeValueAsBytes(n));
        }
        var n=(ObjectNode)mapper.readTree(valid);n.put("capturedAt","2026-09-12T10:00:00+08:00");reject(mapper.writeValueAsBytes(n));
        reject((new String(valid,java.nio.charset.StandardCharsets.UTF_8)+" {}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        reject("{\"contractVersion\":\"3.0\",\"contractVersion\":\"3.0\"}".getBytes());verifyNoInteractions(service);
    }
    @Test void rejectsMissingDuplicateExtraPartsAndOversizeMetadata()throws Exception {
        mvc.perform(multipart(ROUTE).part(metadata(valid))).andExpect(status().isUnprocessableEntity());
        mvc.perform(multipart(ROUTE).part(metadata(valid),file(),file())).andExpect(status().isUnprocessableEntity());
        mvc.perform(multipart(ROUTE).part(metadata(valid),file(),new MockPart("extra",new byte[]{1}))).andExpect(status().isUnprocessableEntity());
        mvc.perform(multipart(ROUTE).part(metadata(new byte[65537]),file())).andExpect(status().isPayloadTooLarge());verifyNoInteractions(service);
    }
    @Test void deniesMissingPrincipalBeforeParsing()throws Exception {
        SecurityContextHolder.clearContext();mvc.perform(multipart(ROUTE)).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    private void reject(byte[] bytes)throws Exception{mvc.perform(multipart(ROUTE).part(metadata(bytes),file())).andExpect(status().isUnprocessableEntity());}
    private MockPart metadata(byte[] bytes){var p=new MockPart("metadata",bytes);p.getHeaders().setContentType(MediaType.APPLICATION_JSON);return p;}
    private MockPart file(){var p=new MockPart("file",new byte[]{1,2});p.getHeaders().setContentType(MediaType.IMAGE_JPEG);return p;}
}
