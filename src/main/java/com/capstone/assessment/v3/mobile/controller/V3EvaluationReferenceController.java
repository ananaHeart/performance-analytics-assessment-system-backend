package com.capstone.assessment.v3.mobile.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3EvaluationReference;
import com.capstone.assessment.v3.mobile.service.V3EvaluationReferenceService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("v3")
public class V3EvaluationReferenceController {
    private final V3EvaluationReferenceService service;
    public V3EvaluationReferenceController(V3EvaluationReferenceService service){this.service=service;}
    @GetMapping("/api/v3/mobile/test-assignments/{assignmentUuid}/evaluation-reference")
    public ResponseEntity<ApiResponse<V3EvaluationReference>> get(@AuthenticationPrincipal V3AuthenticatedUser user,@PathVariable String assignmentUuid){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("Owned evaluation reference retrieved successfully.",service.get(user,assignmentUuid)));
    }
}
