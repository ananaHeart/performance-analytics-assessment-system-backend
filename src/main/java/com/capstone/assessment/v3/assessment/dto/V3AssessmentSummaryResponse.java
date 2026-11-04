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
        int uploadedCount,
        // R2-8: the one status every page shows: draft, active, completed or archived.
        String displayStatus
) {
    /**
     * R2-8: Completed once the assessment closed (its close time passed) or every enrolled
     * learner has a finalized result; archived and draft come first.
     */
    public static String displayStatus(String status, String assignmentStatus, Instant closeAt, Instant now,
                                       int learnerCount, int uploadedCount) {
        if ("archived".equals(status) || "archived".equals(assignmentStatus)) {
            return "archived";
        }
        if ("draft".equals(status)) {
            return "draft";
        }
        if ("completed".equals(status) || "closed".equals(assignmentStatus)
                || (closeAt != null && !closeAt.isAfter(now))
                || (learnerCount > 0 && uploadedCount >= learnerCount)) {
            return "completed";
        }
        return "active";
    }
}
