package com.capstone.assessment.v3.report.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record V3ItemAnalysisReportResponse(
        String reportType,
        Instant generatedAt,
        V3AssessmentResultsReportResponse.Scope scope,
        String dataStatus,
        List<V3AssessmentResultsReportResponse.ReportWarning> warnings,
        List<QuestionRow> rows,
        List<CompetencyMasteryRow> competencyMastery
) {

    public V3ItemAnalysisReportResponse {
        warnings = List.copyOf(warnings);
        rows = List.copyOf(rows);
        competencyMastery = List.copyOf(competencyMastery);
    }

    public record QuestionRow(
            long questionId,
            long testPartId,
            int itemNumber,
            String questionTypeCode,
            int correctCount,
            int incorrectCount,
            int unansweredCount,
            BigDecimal difficultyIndex,
            String difficultyLabel,
            List<Long> skillIds
    ) {
    }

    public record CompetencyMasteryRow(
            long skillId,
            String skillName,
            int assessedItemCount,
            int studentCount,
            BigDecimal masteryPercentage,
            String masteryStatusCode,
            List<StudentSkillMastery> students
    ) {
        public CompetencyMasteryRow {
            students = List.copyOf(students);
        }
    }

    /** Every student with a finalized result, lowest mastery first. Uses the same
     *  masteryStatusCode values and thresholds as the parent competency row. The
     *  recommendation fields come from the school's active "intervention" rule set and are
     *  null when mastery is unknown or no such rule set is configured. */
    public record StudentSkillMastery(
            long studentId,
            String fullName,
            BigDecimal masteryPercentage,
            String masteryStatusCode,
            String recommendationCode,
            String recommendationLabel,
            String suggestion
    ) {
    }
}
