package com.capstone.assessment.v3.mobile.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3DetectionBatch;
import com.capstone.assessment.v3.mobile.dto.V3DetectionUploadResponse;
import com.capstone.assessment.v3.mobile.service.V3DetectionUploadService;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;

/** Implemented handler; the V3 security chain keeps it unavailable pending release validation. */
@RestController
@Profile("v3")
public class V3DetectionUploadController {
    public static final int MAX_BODY_BYTES=128*1024;
    private final V3DetectionUploadService service;
    private final ObjectMapper mapper;
    public V3DetectionUploadController(V3DetectionUploadService service,ObjectMapper mapper) {
        this.service=service;
        ObjectMapper strictMapper=mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        strictMapper.setConfig(strictMapper.getDeserializationConfig().without(MapperFeature.ALLOW_COERCION_OF_SCALARS));
        this.mapper=strictMapper;
    }

    @PostMapping(path="/api/v3/mobile/scan-pages/{scanPageUuid}/detections",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<V3DetectionUploadResponse>> upload(@AuthenticationPrincipal V3AuthenticatedUser user,
            @PathVariable String scanPageUuid,HttpServletRequest request) {
        if(user==null || !"teacher".equals(user.role()) || !"active".equals(user.status()))
            throw new V3AuthException("TEACHER_REQUIRED","An active assigned teacher is required.",HttpStatus.FORBIDDEN);
        V3DetectionBatch batch;
        try {
            if(request.getContentLengthLong()>MAX_BODY_BYTES) throw oversized();
            byte[] bytes=request.getInputStream().readNBytes(MAX_BODY_BYTES+1);
            if(bytes.length>MAX_BODY_BYTES) throw oversized();
            batch=mapper.readValue(bytes,V3DetectionBatch.class);
        } catch(IOException | IllegalArgumentException e) {
            throw new V3AuthException("VALIDATION_FAILED","The body must match the supported detection contract.",HttpStatus.UNPROCESSABLE_ENTITY);
        }
        var response=service.upload(user,scanPageUuid,batch);
        return ResponseEntity.status("replayed".equals(response.disposition())?HttpStatus.OK:HttpStatus.CREATED)
                .body(ApiResponse.success("Detection batch acknowledged.",response));
    }
    private static V3AuthException oversized() {
        return new V3AuthException("PAYLOAD_TOO_LARGE","Detection JSON exceeds 128 KiB.",HttpStatus.PAYLOAD_TOO_LARGE);
    }
}
