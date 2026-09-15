package com.capstone.assessment.v3.mobile.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3CorrectionRequest;
import com.capstone.assessment.v3.mobile.service.V3ResultCorrectionService;
import com.capstone.assessment.v3.scoring.dto.V3ScoredResultResponse;
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
public class V3ResultCorrectionController {
    public static final int MAX_BODY_BYTES=2*1024*1024;
    private final V3ResultCorrectionService service;
    private final ObjectMapper mapper;
    public V3ResultCorrectionController(V3ResultCorrectionService service,ObjectMapper mapper){
        this.service=service;ObjectMapper strictMapper=mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        strictMapper.setConfig(strictMapper.getDeserializationConfig().without(MapperFeature.ALLOW_COERCION_OF_SCALARS));
        this.mapper=strictMapper;
    }
    @PostMapping(path="/api/v3/mobile/results/{resultUuid}/corrections",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<V3ScoredResultResponse>> correct(@AuthenticationPrincipal V3AuthenticatedUser user,@PathVariable String resultUuid,HttpServletRequest request){
        if(user==null)throw new V3AuthException("AUTHENTICATION_REQUIRED","Authentication is required.",HttpStatus.UNAUTHORIZED);
        if(!"teacher".equals(user.role()) || !"active".equals(user.status()))throw new V3AuthException("RESULT_ACCESS_DENIED","An active teacher is required.",HttpStatus.FORBIDDEN);
        V3CorrectionRequest input;
        try{
            if(request.getContentLengthLong()>MAX_BODY_BYTES)throw oversized();
            byte[] bytes=request.getInputStream().readNBytes(MAX_BODY_BYTES+1);if(bytes.length>MAX_BODY_BYTES)throw oversized();
            var tree=mapper.readTree(bytes);checkTimes(tree);input=mapper.treeToValue(tree,V3CorrectionRequest.class);
        }catch(IOException | IllegalArgumentException e){throw new V3AuthException("VALIDATION_FAILED","Use the explicit 3.2 correction JSON contract.",HttpStatus.UNPROCESSABLE_ENTITY);}
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("Correction and official scoring committed; reconcile current result state.",service.correct(user,resultUuid,input)));
    }
    private void checkTimes(JsonNode n){if(n==null)return;if(n.isObject() && n.has("clientDecidedAt")){var t=n.get("clientDecidedAt");if(!t.isTextual() || !t.textValue().endsWith("Z"))throw new IllegalArgumentException("UTC required");}n.elements().forEachRemaining(this::checkTimes);}
    private V3AuthException oversized(){return new V3AuthException("PAYLOAD_TOO_LARGE","Correction JSON exceeds 2 MiB.",HttpStatus.PAYLOAD_TOO_LARGE);}
}
