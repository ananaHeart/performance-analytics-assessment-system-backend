package com.capstone.assessment.v3.assessment.dto;

import java.time.Instant;

public record V3AssessmentSummaryResponse(
        long testId,
        String testUuid,
        long testAssignmentId,
        long classAssignmentId,
        String testName,
        String testType,
        String termName,
        int totalItems,
        String status,
        String assignmentStatus,
        Instant openAt,
        Instant closeAt,
        Instant updatedAt
) {
}
