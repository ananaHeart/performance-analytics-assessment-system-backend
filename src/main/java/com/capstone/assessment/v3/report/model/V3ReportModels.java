package com.capstone.assessment.v3.report.model;

import java.math.BigDecimal;
import java.time.Instant;

public final class V3ReportModels {

    private V3ReportModels() {
    }

    public record AssessmentScopeRow(
            String schoolId,
            String schoolName,
            int academicYearId,
            String academicYearName,
            int termPeriodId,
            String termName,
            long classId,
            int gradeLevelId,
            String gradeLevelName,
            int sectionId,
            String sectionName,
            long classAssignmentId,
            long teacherUserId,
            String teacherName,
            int subjectId,
            String subjectName,
            long testId,
            long testAssignmentId,
            String testName,
            String testType,
            String testStatus,
            String assignmentStatus,
            Instant openAt,
            Instant closeAt,
            BigDecimal configuredMaximumPoints
    ) {
    }

    public record AssessmentResultRow(
            long studentId,
            long classListId,
            String studentLrn,
            String fullName,
            String enrollmentStatus,
            Long testResultId,
            String resultStatus,
            Instant submittedAt,
            Instant verifiedAt,
            BigDecimal earnedPoints,
            BigDecimal maximumPoints,
            BigDecimal percentage,
            String performanceStatusCode,
            Long performanceRuleSetId,
            String performanceRuleSetName,
            String performanceRuleVersion,
            String performanceRuleDefinition,
            int pendingTeacherVerificationCount
    ) {
    }
}
