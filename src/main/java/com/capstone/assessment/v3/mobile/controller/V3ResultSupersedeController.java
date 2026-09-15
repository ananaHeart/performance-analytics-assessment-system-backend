package com.capstone.assessment.v3.mobile.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.*;
import com.capstone.assessment.v3.mobile.service.V3ResultSupersedeService;
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
@RequestMapping("/api/v3/mobile/results/{resultUuid}")
public class V3ResultSupersedeController {
    public static final int MAX_BODY_BYTES=32*1024;
    private final V3ResultSupersedeService service;
    private final ObjectMapper mapper;
    public V3ResultSupersedeController(V3ResultSupersedeService service,ObjectMapper mapper){
        this.service=service;ObjectMapper strictMapper=mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        strictMapper.setConfig(strictMapper.getDeserializationConfig().without(MapperFeature.ALLOW_COERCION_OF_SCALARS));
        this.mapper=strictMapper;
    }
    @PostMapping(path="/supersede",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<V3LifecycleAck>> supersede(@AuthenticationPrincipal V3AuthenticatedUser user,@PathVariable String resultUuid,HttpServletRequest request){
        if(user==null)throw new V3AuthException("AUTHENTICATION_REQUIRED","Authentication is required.",HttpStatus.UNAUTHORIZED);
        if(!"teacher".equals(user.role()) || !"active".equals(user.status()))throw new V3AuthException("RESULT_ACCESS_DENIED","An active teacher is required.",HttpStatus.FORBIDDEN);
        V3SupersedeRequest input;
        try{
            if(request.getContentLengthLong()>MAX_BODY_BYTES)throw oversized();
            byte[] bytes=request.getInputStream().readNBytes(MAX_BODY_BYTES+1);if(bytes.length>MAX_BODY_BYTES)throw oversized();
            input=mapper.readValue(bytes,V3SupersedeRequest.class);
        }catch(IOException | IllegalArgumentException e){throw new V3AuthException("VALIDATION_FAILED","Use the explicit 3.0 supersede JSON contract.",HttpStatus.UNPROCESSABLE_ENTITY);}
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("Supersede acknowledged; retrieve current result state for reconciliation.",service.supersede(user,resultUuid,input)));
    }
    private V3AuthException oversized(){return new V3AuthException("PAYLOAD_TOO_LARGE","Supersede JSON exceeds 32 KiB.",HttpStatus.PAYLOAD_TOO_LARGE);}
    @GetMapping("/supersession")
    public ResponseEntity<ApiResponse<V3LifecycleAck>> supersession(@AuthenticationPrincipal V3AuthenticatedUser user,@PathVariable String resultUuid){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.supersession(user,resultUuid)));
    }
}
