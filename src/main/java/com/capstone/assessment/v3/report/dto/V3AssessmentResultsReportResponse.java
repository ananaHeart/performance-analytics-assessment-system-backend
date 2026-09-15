package com.capstone.assessment.v3.report.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record V3AssessmentResultsReportResponse(
        String reportType,
        Instant generatedAt,
        Scope scope,
        CalculationPolicy calculationPolicy,
        String dataStatus,
        List<ReportWarning> warnings,
        Summary summary,
        List<StudentResultRow> rows
) {

    public record Scope(
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
            Instant closeAt
    ) {
    }

    public record CalculationPolicy(
            String scoreSource,
            String inclusionRule,
            String percentageFormula,
            String rounding,
            List<PerformanceRuleSetReference> performanceRuleSets
    ) {
    }

    public record PerformanceRuleSetReference(
            long performanceRuleSetId,
            String ruleSetName,
            String ruleVersion
    ) {
    }

    public record ReportWarning(
            String code,
            String message
    ) {
    }

    public record Summary(
            int studentCount,
            int submittedCount,
            int verifiedCount,
            int pendingCount,
            BigDecimal maximumPoints,
            BigDecimal classMeanPoints,
            BigDecimal classMeanPercentage
    ) {
    }

    public record StudentResultRow(
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
            String performanceStatusLabel,
            Integer pendingTeacherVerificationCount
    ) {
    }
}
