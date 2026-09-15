package com.capstone.assessment.v2.assessment.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentReferenceDataResponse;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentRequest;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentResponse;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentSummaryResponse;
import com.capstone.assessment.v2.assessment.service.V2OmrSheetPrintService;
import com.capstone.assessment.v2.assessment.service.V2AssessmentService;
import com.capstone.assessment.v2.assessment.service.V2QuestionnairePdfService;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import com.capstone.assessment.v2.auth.service.V2RequestMetadata;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
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

@Profile("v2")
@RestController
@RequestMapping("/api/v2/assessments")
public class V2AssessmentController {

    private static final String DEVICE_IDENTIFIER_HEADER = "X-Device-Identifier";

    private final V2AssessmentService assessmentService;
    private final V2OmrSheetPrintService omrSheetPrintService;
    private final V2QuestionnairePdfService questionnairePdfService;

    public V2AssessmentController(
            V2AssessmentService assessmentService,
            V2OmrSheetPrintService omrSheetPrintService,
            V2QuestionnairePdfService questionnairePdfService
    ) {
        this.assessmentService = assessmentService;
        this.omrSheetPrintService = omrSheetPrintService;
        this.questionnairePdfService = questionnairePdfService;
    }

    @GetMapping("/reference-data")
    public ResponseEntity<ApiResponse<V2AssessmentReferenceDataResponse>> getReferenceData(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @RequestParam(required = false) Long classAssignmentId,
            @RequestParam(required = false) Integer termPeriodId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Assessment reference data retrieved successfully.",
                assessmentService.getReferenceData(principal, classAssignmentId, termPeriodId)
        ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<V2AssessmentResponse>> createAssessment(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @Valid @RequestBody V2AssessmentRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Draft assessment created successfully.",
                assessmentService.createAssessment(principal, request, requestMetadata(httpRequest))
        ));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<V2AssessmentSummaryResponse>>> listAssessments(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @RequestParam(required = false) Long classAssignmentId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Assessments retrieved successfully.",
                assessmentService.listAssessments(principal, classAssignmentId)
        ));
    }

    @GetMapping("/{testId}")
    public ResponseEntity<ApiResponse<V2AssessmentResponse>> getAssessment(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @PathVariable long testId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Assessment retrieved successfully.",
                assessmentService.getAssessment(principal, testId)
        ));
    }

    @GetMapping(value = "/{testId}/omr-sheet", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> printOmrSheet(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @PathVariable long testId
    ) {
        V2AssessmentResponse assessment = assessmentService.getAssessment(principal, testId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .headers(headers -> headers.setContentDisposition(ContentDisposition.inline()
                        .filename("bubble-answer-sheet-%d.pdf".formatted(testId))
                        .build()))
                .body(omrSheetPrintService.renderSheet(assessment));
    }

    @GetMapping(value = "/{testId}/questionnaire", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadQuestionnaire(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @PathVariable long testId
    ) {
        V2AssessmentResponse assessment = assessmentService.getAssessment(principal, testId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .headers(headers -> headers.setContentDisposition(ContentDisposition.attachment()
                        .filename("test-questionnaire-%d.pdf".formatted(testId))
                        .build()))
                .body(questionnairePdfService.renderQuestionnaire(assessment));
    }

    @PutMapping("/{testId}")
    public ResponseEntity<ApiResponse<V2AssessmentResponse>> updateAssessment(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @PathVariable long testId,
            @Valid @RequestBody V2AssessmentRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Draft assessment updated successfully.",
                assessmentService.updateAssessment(principal, testId, request, requestMetadata(httpRequest))
        ));
    }

    @PostMapping("/{testId}/activate")
    public ResponseEntity<ApiResponse<V2AssessmentResponse>> activateAssessment(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @PathVariable long testId,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Assessment activated successfully.",
                assessmentService.activateAssessment(principal, testId, requestMetadata(httpRequest))
        ));
    }

    @PostMapping("/{testId}/archive")
    public ResponseEntity<ApiResponse<V2AssessmentResponse>> archiveAssessment(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @PathVariable long testId,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Assessment archived successfully.",
                assessmentService.archiveAssessment(principal, testId, requestMetadata(httpRequest))
        ));
    }

    private V2RequestMetadata requestMetadata(HttpServletRequest request) {
        return new V2RequestMetadata(
                clientIpAddress(request),
                request.getHeader("User-Agent"),
                request.getHeader(DEVICE_IDENTIFIER_HEADER)
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
