package com.capstone.assessment.v3.report.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * "Identify the learning competency": for one Term + Grade Level + Subject, every root
 * competency, the specific competencies (skills) under it least mastered first, and the
 * students who are weak on each. Aggregated across ALL finalized assessments in that term,
 * not a single test. Teachers only see their own classes; the principal sees the school.
 */
public record V3LearningCompetencyReportResponse(
        String reportType,
        Instant generatedAt,
        Scope scope,
        String dataStatus,
        List<V3AssessmentResultsReportResponse.ReportWarning> warnings,
        List<IncludedAssessment> assessments,
        List<RootCompetency> rootCompetencies,
        // Signature context: the signed-in user who generated the report.
        String preparedByName,
        String preparedByRole
) {

    public V3LearningCompetencyReportResponse {
        warnings = List.copyOf(warnings);
        assessments = List.copyOf(assessments);
        rootCompetencies = List.copyOf(rootCompetencies);
    }

    public record Scope(
            String schoolId,
            String schoolName,
            int academicYearId,
            String academicYearName,
            int termPeriodId,
            String termName,
            int gradeLevelId,
            String gradeLevelName,
            int subjectId,
            String subjectName
    ) {
    }

    /** An assessment that has at least one finalized result in this scope. */
    public record IncludedAssessment(
            long testId,
            String testName
    ) {
    }

    /** Least mastered first. masteryPercentage is weighted by points across its skills. */
    public record RootCompetency(
            long rootTagId,
            String rootTagName,
            BigDecimal masteryPercentage,
            String masteryStatusCode,
            List<Skill> skills
    ) {
        public RootCompetency {
            skills = List.copyOf(skills);
        }
    }

    /** A specific competency, least mastered first. studentCount is every student assessed
     *  on it; weakStudents only lists those below the 80% mastery line, weakest first.
     *  The recommendation fields are the class-level intervention for this skill, picked by
     *  the skill's overall masteryPercentage from the school's "intervention" rule set, and
     *  are null when no such rule set is configured. */
    public record Skill(
            long skillId,
            long competencyId,
            String competencyName,
            int studentCount,
            BigDecimal masteryPercentage,
            String masteryStatusCode,
            String recommendationCode,
            String recommendationLabel,
            String suggestion,
            int weakStudentCount,
            List<WeakStudent> weakStudents
    ) {
        public Skill {
            weakStudents = List.copyOf(weakStudents);
        }
    }

    /** Recommendation fields come from the school's active "intervention" rule set and are
     *  null when no such rule set is configured. */
    public record WeakStudent(
            long studentId,
            String fullName,
            String sectionName,
            int assessmentCount,
            BigDecimal masteryPercentage,
            String masteryStatusCode,
            String recommendationCode,
            String recommendationLabel,
            String suggestion
    ) {
    }
}
