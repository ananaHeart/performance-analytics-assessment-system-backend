package com.capstone.assessment.v3.report.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.report.dto.V3AssessmentResultsReportResponse;
import com.capstone.assessment.v3.report.dto.V3ReportReferenceDataResponse;
import com.capstone.assessment.v3.report.service.V3ReportService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("v3")
@RestController
@RequestMapping("/api/v3/reports")
public class V3ReportController {

    private final V3ReportService reportService;

    public V3ReportController(V3ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/reference-data")
    public ResponseEntity<ApiResponse<V3ReportReferenceDataResponse>> referenceData(
            @AuthenticationPrincipal V3AuthenticatedUser user
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 report reference data retrieved successfully.",
                reportService.getReferenceData(user)
        ));
    }

    @GetMapping("/assessment-results")
    public ResponseEntity<ApiResponse<V3AssessmentResultsReportResponse>> assessmentResults(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @RequestParam long testId,
            @RequestParam long classAssignmentId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 assessment-results report generated successfully.",
                reportService.getAssessmentResults(user, testId, classAssignmentId)
        ));
    }
}
