package com.capstone.assessment.v2.analytics.dto;

import java.util.List;

public record V2SchoolAnalyticsResponse(
        int totalAssessments,
        List<V2LmsResponse> lms,
        List<V2GradeMasteryResponse> gradeLevels,
        List<V2AssessmentTrendResponse> trends
) {
}
