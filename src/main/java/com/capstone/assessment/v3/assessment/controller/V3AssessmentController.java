package com.capstone.assessment.v3.assessment.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.assessment.dto.V3AssessmentReferenceDataResponse;
import com.capstone.assessment.v3.assessment.dto.V3AssessmentRequest;
import com.capstone.assessment.v3.assessment.dto.V3AssessmentResponse;
import com.capstone.assessment.v3.assessment.dto.V3AssessmentSummaryResponse;
import com.capstone.assessment.v3.assessment.service.V3AssessmentService;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Profile("v3")
@RestController
@RequestMapping("/api/v3/assessments")
public class V3AssessmentController {

    private final V3AssessmentService assessmentService;

    public V3AssessmentController(V3AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @GetMapping("/reference-data")
    public ResponseEntity<ApiResponse<V3AssessmentReferenceDataResponse>> referenceData(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @RequestParam(required = false) Long classAssignmentId,
            @RequestParam(required = false) Integer termPeriodId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 assessment reference data retrieved successfully.",
                assessmentService.getReferenceData(user, classAssignmentId, termPeriodId)
        ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<V3AssessmentResponse>> create(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @Valid @RequestBody V3AssessmentRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "V3 draft assessment created successfully.",
                assessmentService.createAssessment(user, request, requestMetadata(httpRequest))
        ));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<V3AssessmentSummaryResponse>>> list(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @RequestParam long classAssignmentId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 assessments retrieved successfully.",
                assessmentService.listAssessments(user, classAssignmentId)
        ));
    }

    @GetMapping("/{testId}")
    public ResponseEntity<ApiResponse<V3AssessmentResponse>> get(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @PathVariable long testId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 assessment retrieved successfully.",
                assessmentService.getAssessment(user, testId)
        ));
    }

    @PutMapping("/{testId}")
    public ResponseEntity<ApiResponse<V3AssessmentResponse>> update(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @PathVariable long testId,
            @Valid @RequestBody V3AssessmentRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 draft assessment updated successfully.",
                assessmentService.updateAssessment(user, testId, request, requestMetadata(httpRequest))
        ));
    }

    @PostMapping("/{testId}/activate")
    public ResponseEntity<ApiResponse<V3AssessmentResponse>> activate(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @PathVariable long testId,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 assessment activated successfully.",
                assessmentService.activateAssessment(user, testId, requestMetadata(httpRequest))
        ));
    }

    @PostMapping("/{testId}/archive")
    public ResponseEntity<ApiResponse<V3AssessmentResponse>> archive(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @PathVariable long testId,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 assessment archived successfully.",
                assessmentService.archiveAssessment(user, testId, requestMetadata(httpRequest))
        ));
    }

    private V3RequestMetadata requestMetadata(HttpServletRequest request) {
        return new V3RequestMetadata(
                clientIpAddress(request),
                request.getHeader("User-Agent"),
                null
        );
    }

    private String clientIpAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return request.getRemoteAddr();
        }
        return forwardedFor.split(",", 2)[0].trim();
    }
}
