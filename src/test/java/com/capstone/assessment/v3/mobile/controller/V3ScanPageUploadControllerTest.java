package com.capstone.assessment.v3.mobile.controller;

import com.capstone.assessment.v3.auth.exception.V3AuthExceptionHandler;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3ScanPageUploadMetadata;
import com.capstone.assessment.v3.mobile.dto.V3ScanPageUploadResponse;
import com.capstone.assessment.v3.mobile.service.V3ScanPageIngestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockPart;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Handler-level tests intentionally bypass the release gate; separate tests exercise real V3 security. */
class V3ScanPageUploadControllerTest {
    private static final String ROUTE = "/api/v3/mobile/scan-pages";
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final V3ScanPageIngestionService service = mock(V3ScanPageIngestionService.class);
    private final V3AuthenticatedUser user = new V3AuthenticatedUser(42, "S1", "teacher@test", "teacher", "active", "session");
    private MockMvc mvc;
    private byte[] valid;

    @BeforeEach void setup() throws Exception {
        try (var input = getClass().getResourceAsStream("/contracts/v3/mobile/scan-page-upload-metadata.json")) {
            valid = input.readAllBytes();
        }
        mvc = MockMvcBuilders.standaloneSetup(new V3ScanPageUploadController(service, mapper))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new V3AuthExceptionHandler()).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        when(service.ingest(eq(user), any(), any())).thenAnswer(call -> {
            V3ScanPageUploadMetadata metadata = call.getArgument(1);
            return new V3ScanPageUploadResponse(metadata.syncUuid(), metadata.resultUuid(), metadata.scanUuid(),
                    metadata.scanPageUuid(), 123, "created", "captured", metadata.imageHash(), Instant.now());
        });
    }

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void acceptsJsonMetadataWithoutFilenameAndPreservesResponseContract() throws Exception {
        mvc.perform(multipart(ROUTE).part(metadata(valid), image()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.backendScanPageId").value(123))
                .andExpect(jsonPath("$.data.uploadStatus").value("created"))
                .andExpect(jsonPath("$.data.storageKey").doesNotExist());
        var captured = ArgumentCaptor.forClass(MultipartFile.class);
        verify(service).ingest(eq(user), any(), captured.capture());
        assertNull(captured.getValue().getOriginalFilename());
        assertArrayEquals(new byte[]{1, 2, 3}, captured.getValue().getInputStream().readAllBytes());
    }

    @Test void acceptsNextCaptureAndRejectsFractionalCaptureNumber()throws Exception{
        var json=(ObjectNode)mapper.readTree(valid);json.put("captureNumber",2.5);
        mvc.perform(multipart(ROUTE).part(metadata(mapper.writeValueAsBytes(json)),image())).andExpect(status().isUnprocessableEntity());verifyNoInteractions(service);
        json.put("captureNumber",2);mvc.perform(multipart(ROUTE).part(metadata(mapper.writeValueAsBytes(json)),image())).andExpect(status().isCreated());
        var captured=ArgumentCaptor.forClass(V3ScanPageUploadMetadata.class);verify(service).ingest(eq(user),captured.capture(),any());assertEquals(2,captured.getValue().captureNumber());
    }
    @Test void durableReplayUsesHttp200() throws Exception {
        var metadata = mapper.readValue(valid, V3ScanPageUploadMetadata.class);
        when(service.ingest(eq(user), any(), any())).thenReturn(new V3ScanPageUploadResponse(metadata.syncUuid(),
                metadata.resultUuid(), metadata.scanUuid(), metadata.scanPageUuid(), 123, "replayed", "captured", metadata.imageHash(), Instant.now()));
        mvc.perform(multipart(ROUTE).part(metadata(valid), image())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadStatus").value("replayed"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"score", "comments", "detections", "centralScanId", "localImagePath"})
    void rejectsFieldsOutsideFrozenUploadDto(String field) throws Exception {
        ObjectNode json = (ObjectNode) mapper.readTree(valid);
        json.put(field, "untrusted");
        mvc.perform(multipart(ROUTE).part(metadata(mapper.writeValueAsBytes(json)), image()))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{} {}", "{not-json}", "{\"scanUuid\":\"a\",\"scanUuid\":\"b\"}",
            "{\"classListId\":null}", "{\"classListId\":\"41001\"}"})
    void rejectsMalformedDuplicateOrCoercedMetadata(String json) throws Exception {
        mvc.perform(multipart(ROUTE).part(metadata(json.getBytes(StandardCharsets.UTF_8)), image()))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(service);
    }

    @Test void rejectsMissingDuplicateAndExtraParts() throws Exception {
        mvc.perform(multipart(ROUTE).part(metadata(valid))).andExpect(status().isUnprocessableEntity());
        mvc.perform(multipart(ROUTE).part(metadata(valid), image(), image())).andExpect(status().isUnprocessableEntity());
        mvc.perform(multipart(ROUTE).part(metadata(valid), image(), new MockPart("extra", new byte[]{1})))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(service);
    }

    @Test void rejectsWrongMetadataMimeAndOversizeDeclaredOrActualBytes() throws Exception {
        MockPart wrongMime = metadata(valid);
        wrongMime.getHeaders().setContentType(MediaType.TEXT_PLAIN);
        mvc.perform(multipart(ROUTE).part(wrongMime, image())).andExpect(status().isUnprocessableEntity());
        byte[] oversized = new byte[V3ScanPageUploadController.MAX_METADATA_BYTES + 1];
        mvc.perform(multipart(ROUTE).part(metadata(oversized), image())).andExpect(status().isPayloadTooLarge());
        MockPart lying = new MockPart("metadata", oversized) { @Override public long getSize() { return 1; } };
        lying.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        mvc.perform(multipart(ROUTE).part(lying, image())).andExpect(status().isPayloadTooLarge());
        verifyNoInteractions(service);
    }

    @Test void refusesMissingPrincipalBeforeParsingOrCallingIngestion() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(multipart(ROUTE)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    private MockPart metadata(byte[] bytes) {
        MockPart part = new MockPart("metadata", bytes);
        part.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return part;
    }
    private MockPart image() {
        MockPart part = new MockPart("image", "../../device.jpg", new byte[]{1, 2, 3});
        part.getHeaders().setContentType(MediaType.IMAGE_JPEG);
        return part;
    }
}
