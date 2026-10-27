package com.capstone.assessment.v3.assessment.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.assessment.dto.V3AssessmentDetailsRequest;
import com.capstone.assessment.v3.assessment.dto.V3AssessmentEditEligibilityResponse;
import com.capstone.assessment.v3.assessment.dto.V3AssessmentReferenceDataResponse;
import com.capstone.assessment.v3.assessment.dto.V3AssessmentRequest;
import com.capstone.assessment.v3.assessment.dto.V3AssessmentResponse;
import com.capstone.assessment.v3.assessment.dto.V3AssessmentSummaryResponse;
import com.capstone.assessment.v3.assessment.service.V3AssessmentService;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
import com.capstone.assessment.v3.report.service.V3QuestionnairePdfRenderer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final V3QuestionnairePdfRenderer questionnaireRenderer;

    public V3AssessmentController(
            V3AssessmentService assessmentService,
            V3QuestionnairePdfRenderer questionnaireRenderer
    ) {
        this.assessmentService = assessmentService;
        this.questionnaireRenderer = questionnaireRenderer;
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

    /** The printable Test Questionnaire: questions and choices only, never the answer keys. */
    @GetMapping("/{testId}/questionnaire/pdf")
    public ResponseEntity<byte[]> questionnairePdf(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @PathVariable long testId
    ) {
        byte[] pdf = questionnaireRenderer.render(user, assessmentService.getAssessment(user, testId));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=test-questionnaire-%d.pdf".formatted(testId))
                .body(pdf);
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

    @PostMapping("/{testId}/restore")
    public ResponseEntity<ApiResponse<V3AssessmentResponse>> restore(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @PathVariable long testId,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 assessment restored successfully.",
                assessmentService.restoreAssessment(user, testId, requestMetadata(httpRequest))
        ));
    }

    /** ASM-7: what the teacher may do with this assessment now. */
    @GetMapping("/{testId}/edit-eligibility")
    public ResponseEntity<ApiResponse<V3AssessmentEditEligibilityResponse>> editEligibility(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @PathVariable long testId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Assessment edit eligibility retrieved successfully.",
                assessmentService.getEditEligibility(user, testId)
        ));
    }

    /** ASM-7: change only the title and instructions (allowed after activation). */
    @PatchMapping("/{testId}/details")
    public ResponseEntity<ApiResponse<V3AssessmentResponse>> updateDetails(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @PathVariable long testId,
            @Valid @RequestBody V3AssessmentDetailsRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Assessment details updated successfully.",
                assessmentService.updateAssessmentDetails(user, testId, request, requestMetadata(httpRequest))
        ));
    }

    /** ASM-7: delete an assessment made by mistake (only when nothing was captured yet). */
    @DeleteMapping("/{testId}")
    public ResponseEntity<ApiResponse<V3AssessmentEditEligibilityResponse>> delete(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @PathVariable long testId,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Assessment deleted successfully.",
                assessmentService.deleteAssessment(user, testId, requestMetadata(httpRequest))
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
