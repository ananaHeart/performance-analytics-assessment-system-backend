package com.capstone.assessment.v2.analytics.model;

public record V2AnalyticsTestContext(
        long testId,
        long teacherUserId,
        String schoolId,
        String status
) {
}
