package com.capstone.assessment.v3.report.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record V3StudentPerformanceProfileResponse(
        String reportType,
        Instant generatedAt,
        long studentId,
        String studentLrn,
        String fullName,
        String gradeLevelName,
        String sectionName,
        String dataStatus,
        List<V3AssessmentResultsReportResponse.ReportWarning> warnings,
        List<AssessmentResult> assessmentResults,
        List<CompetencyPerformance> competencyPerformance,
        List<InterventionSuggestion> interventions,
        // Report header / signature context. preparedBy is the signed-in user who generated
        // the report (the teacher, or the principal), not necessarily the class teacher.
        String schoolName,
        String academicYearName,
        String preparedByName,
        String preparedByRole
) {

    public V3StudentPerformanceProfileResponse {
        warnings = List.copyOf(warnings);
        assessmentResults = List.copyOf(assessmentResults);
        competencyPerformance = List.copyOf(competencyPerformance);
        interventions = List.copyOf(interventions);
    }

    public record AssessmentResult(
            long testId,
            String testName,
            int termPeriodId,
            String termName,
            BigDecimal percentage,
            String resultStatus,
            Instant completedAt,
            String subjectName,
            // Finalized results only; null while pending verification.
            BigDecimal earnedPoints,
            BigDecimal maximumPoints,
            String performanceStatusCode,
            // When the assessment was conducted (its open/close schedule); completedAt is when
            // this student's result was checked.
            Instant openAt,
            Instant closeAt
    ) {
    }

    public record CompetencyPerformance(
            long skillId,
            String skillName,
            BigDecimal masteryPercentage,
            String masteryStatusCode
    ) {
    }

    /** Auto-generated from the school's active "intervention" rule set. Only skills that need
     *  action (review / reteach / priority_intervention) are listed, most urgent first -
     *  mastered skills stay in competencyPerformance. This is not the LMS case-management
     *  feature (student_intervention_cases), which is still unbuilt. */
    public record InterventionSuggestion(
            long skillId,
            String skillName,
            BigDecimal masteryPercentage,
            String recommendationCode,
            String recommendationLabel,
            String suggestion
    ) {
    }
}
