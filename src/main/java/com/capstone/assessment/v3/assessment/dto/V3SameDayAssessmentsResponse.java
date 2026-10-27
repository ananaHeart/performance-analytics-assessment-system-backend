package com.capstone.assessment.v3.assessment.dto;

import java.time.Instant;
import java.util.List;

/**
 * GEN-2 (BUG-1 follow-up): other assessments of the same class assignment opening on the same
 * day. Only a warning; the teacher may still save.
 */
public record V3SameDayAssessmentsResponse(
        boolean hasSameDayAssessments,
        String message,
        List<Item> assessments
) {
    public record Item(long testId, String testName, String status, Instant openAt) {
    }
}
