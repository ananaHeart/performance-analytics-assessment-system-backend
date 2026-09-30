package com.capstone.assessment.v3.report.dto;

import java.time.Instant;
import java.util.List;

/**
 * Teacher synchronization activity for the principal: which teachers are up to date, which
 * assessments they have synced, and which results still failed to upload. Deliberately one
 * row per teacher and one per assessment - never one per sync, which grows with class size.
 */
public record V3SyncActivityReportResponse(
        String reportType,
        Instant generatedAt,
        Instant windowStart,
        Instant windowEnd,
        String dataStatus,
        List<V3AssessmentResultsReportResponse.ReportWarning> warnings,
        List<TeacherSummary> teachers,
        List<AssessmentSync> assessments,
        // Report header / signature context: the school, and the signed-in user who generated it.
        String schoolName,
        String preparedByName,
        String preparedByRole
) {

    public V3SyncActivityReportResponse {
        warnings = List.copyOf(warnings);
        teachers = List.copyOf(teachers);
        assessments = List.copyOf(assessments);
    }

    /** Every teacher in scope, most recently synced first; lastSyncedAt is null if never. */
    public record TeacherSummary(
            long teacherUserId,
            String teacherName,
            Instant lastSyncedAt,
            int assessmentsSynced,
            int resultsNotUploaded
    ) {
    }

    /** resultsNotUploaded counts results whose latest upload attempt failed; failures that a
     *  later retry fixed are not counted. */
    public record AssessmentSync(
            long teacherUserId,
            String teacherName,
            long testAssignmentId,
            String assessmentName,
            String className,
            Instant lastSyncedAt,
            int resultsNotUploaded,
            String uploadErrorDetails
    ) {
    }
}
