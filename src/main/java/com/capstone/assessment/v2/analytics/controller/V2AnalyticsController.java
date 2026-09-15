package com.capstone.assessment.v2.analytics.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v2.analytics.dto.V2ItemAnalysisResponse;
import com.capstone.assessment.v2.analytics.dto.V2LmsResponse;
import com.capstone.assessment.v2.analytics.dto.V2SchoolAnalyticsResponse;
import com.capstone.assessment.v2.analytics.dto.V2SyncActivityResponse;
import com.capstone.assessment.v2.analytics.dto.V2TestPartResultResponse;
import com.capstone.assessment.v2.analytics.service.V2AnalyticsService;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Profile("v2")
@RestController
@RequestMapping("/api/v2/analytics")
public class V2AnalyticsController {

    private final V2AnalyticsService analyticsService;

    public V2AnalyticsController(V2AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/lms")
    public ResponseEntity<ApiResponse<List<V2LmsResponse>>> getLms(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @RequestParam long testId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Assessment skill mastery retrieved successfully.",
                analyticsService.getLms(principal, testId)
        ));
    }

    @GetMapping("/item-analysis")
    public ResponseEntity<ApiResponse<List<V2ItemAnalysisResponse>>> getItemAnalysis(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @RequestParam long testId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Item analysis retrieved successfully.",
                analyticsService.getItemAnalysis(principal, testId)
        ));
    }

    @GetMapping("/test-part-results")
    public ResponseEntity<ApiResponse<List<V2TestPartResultResponse>>> getTestPartResults(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @RequestParam long testId,
            @RequestParam long testPartId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Test-part student results retrieved successfully.",
                analyticsService.getTestPartResults(principal, testId, testPartId)
        ));
    }

    @GetMapping("/sync-activity")
    public ResponseEntity<ApiResponse<List<V2SyncActivityResponse>>> getSyncActivity(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @RequestParam(required = false) Long gradeLevelId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long teacherUserId,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long classId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Synchronization activity retrieved successfully.",
                analyticsService.getSyncActivity(
                        principal,
                        gradeLevelId,
                        sectionId,
                        teacherUserId,
                        subjectId,
                        classId
                )
        ));
    }

    @GetMapping("/school-overview")
    public ResponseEntity<ApiResponse<V2SchoolAnalyticsResponse>> getSchoolOverview(
            @AuthenticationPrincipal V2AuthenticatedUser principal,
            @RequestParam(required = false) Long gradeLevelId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long teacherUserId,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long classId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "School analytics retrieved successfully.",
                analyticsService.getSchoolOverview(
                        principal,
                        gradeLevelId,
                        sectionId,
                        teacherUserId,
                        subjectId,
                        classId
                )
        ));
    }
}
