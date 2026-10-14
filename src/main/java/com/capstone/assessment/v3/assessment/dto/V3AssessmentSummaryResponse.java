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
        Instant updatedAt,
        // Enrolled learners of the class, and how many of them have a finalized result:
        // equal (and above zero) means every learner's result is in.
        int learnerCount,
        int uploadedCount
) {
}
