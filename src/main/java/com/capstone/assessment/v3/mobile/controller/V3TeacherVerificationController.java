package com.capstone.assessment.v3.mobile.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.*;
import com.capstone.assessment.v3.mobile.service.V3TeacherVerificationService;
import com.capstone.assessment.v3.mobile.service.V3WrittenVerificationService;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;

@RestController
@Profile("v3")
public class V3TeacherVerificationController {
    public static final int MAX_BODY_BYTES=2*1024*1024;
    private final V3TeacherVerificationService service;
    private final V3WrittenVerificationService written;
    private final ObjectMapper mapper;
    public V3TeacherVerificationController(V3TeacherVerificationService service,ObjectMapper mapper,V3WrittenVerificationService written) {
        this.service=service;
        this.written=written;
        ObjectMapper strictMapper=mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        strictMapper.setConfig(strictMapper.getDeserializationConfig().without(MapperFeature.ALLOW_COERCION_OF_SCALARS));
        this.mapper=strictMapper;
    }
    @PostMapping(path="/api/v3/mobile/verification-batches",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<V3VerificationResponse>> verify(@AuthenticationPrincipal V3AuthenticatedUser user,HttpServletRequest request) {
        if(user==null || !"teacher".equals(user.role()) || !"active".equals(user.status()))
            throw new V3AuthException("VERIFICATION_FORBIDDEN","An active teacher is required.",HttpStatus.FORBIDDEN);
        V3VerificationBatch batch=null;V3WrittenVerificationBatch writtenBatch=null;
        try {
            if(request.getContentLengthLong()>MAX_BODY_BYTES)throw oversized();
            byte[] bytes=request.getInputStream().readNBytes(MAX_BODY_BYTES+1);if(bytes.length>MAX_BODY_BYTES)throw oversized();
            JsonNode tree=mapper.readTree(bytes);checkTimes(tree);
            if(tree!=null && "3.1".equals(tree.path("contractVersion").asText()))writtenBatch=mapper.treeToValue(tree,V3WrittenVerificationBatch.class);
            else batch=mapper.treeToValue(tree,V3VerificationBatch.class);
        } catch(IOException | IllegalArgumentException e) {
            throw new V3AuthException("VALIDATION_FAILED","Use the explicit 3.0 objective or 3.1 written verification JSON contract.",HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("Verification batch processed; inspect every item outcome.",
                writtenBatch==null?service.verify(user,batch):written.verify(user,writtenBatch)));
    }
    private void checkTimes(JsonNode node) {
        if(node==null)return;
        if(node.isObject() && node.has("clientDecidedAt")) {
            var time=node.get("clientDecidedAt");
            if(!time.isTextual() || !time.textValue().endsWith("Z"))throw new IllegalArgumentException("UTC string required");
        }
        node.elements().forEachRemaining(this::checkTimes);
    }
    private static V3AuthException oversized(){return new V3AuthException("PAYLOAD_TOO_LARGE","Verification JSON exceeds 2 MiB.",HttpStatus.PAYLOAD_TOO_LARGE);}
}
