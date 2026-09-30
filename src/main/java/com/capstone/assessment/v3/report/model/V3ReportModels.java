package com.capstone.assessment.v3.report.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
            String resultUuid,
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

    public record ItemAnalysisRow(
            long questionId,
            long testPartId,
            int itemNumber,
            String questionTypeCode,
            int correctCount,
            int incorrectCount,
            int unansweredCount,
            List<Long> skillIds
    ) {
    }

    public record SkillMasteryRow(
            long skillId,
            String competencyName,
            int assessedItemCount,
            int studentCount,
            BigDecimal earnedPoints,
            BigDecimal possiblePointsPerStudent
    ) {
    }

    /** One skill's distinct assessed items and their total points, deduplicated across every
     *  part_skill_mappings row that references the skill (a skill can have more than one range). */
    public record SkillItemTotalRow(
            long skillId,
            String competencyName,
            int assessedItemCount,
            BigDecimal possiblePointsPerStudent
    ) {
    }

    /** One skill's finalized-answer totals, deduplicated the same way. */
    public record SkillAnswerTotalRow(
            long skillId,
            int studentCount,
            BigDecimal earnedPoints
    ) {
    }

    /** Closed set of grouping dimensions for the Principal Consolidated Report. Deliberately an
     *  enum, not a free-text column name, so the repository can map each value to a trusted,
     *  hardcoded SQL column expression rather than ever interpolating caller-supplied text. */
    public enum V3ReportGroupDimension {
        ACADEMIC_YEAR, TERM_PERIOD, GRADE_LEVEL, CLASS, TEACHER, SUBJECT
    }

    public record ConsolidatedGroupRow(
            String groupKey,
            String groupLabel,
            int studentCount,
            BigDecimal earnedPoints,
            BigDecimal possiblePoints
    ) {
    }

    public record ConsolidatedStatusCountRow(
            String groupKey,
            String performanceStatus,
            int count
    ) {
    }

    public record ConsolidatedSkillItemTotalRow(
            String groupKey,
            long skillId,
            String competencyName,
            int assessedItemCount,
            BigDecimal possiblePointsPerStudent
    ) {
    }

    public record ConsolidatedSkillAnswerTotalRow(
            String groupKey,
            long skillId,
            int studentCount,
            BigDecimal earnedPoints
    ) {
    }

    public record StudentAssessmentHistoryRow(
            long testId,
            long testAssignmentId,
            String testName,
            int termPeriodId,
            String termName,
            String resultStatus,
            BigDecimal earnedPoints,
            BigDecimal maximumPoints,
            BigDecimal percentage,
            Instant completedAt,
            String subjectName,
            String performanceStatusCode,
            // The assessment's schedule, i.e. when it was conducted.
            Instant openAt,
            Instant closeAt
    ) {
    }

    public record StudentClassContext(
            String gradeLevelName,
            String sectionName,
            String academicYearName
    ) {
    }

    /** One finalized result's earned points on one skill (Item Analysis "students per skill"). */
    public record StudentSkillMasteryRow(
            long skillId,
            long testResultId,
            long studentId,
            String fullName,
            BigDecimal earnedPoints
    ) {
    }

    public record StudentSkillItemTotalRow(
            long skillId,
            String competencyName,
            int assessedItemCount,
            BigDecimal possiblePoints
    ) {
    }

    public record StudentSkillAnswerTotalRow(
            long skillId,
            BigDecimal earnedPoints
    ) {
    }

    /** Display names for a Learning Competency report's Term + Grade Level + Subject filter.
     *  gradeLevelName / subjectName are null when that id does not exist. */
    public record CompetencyScopeRow(
            String schoolId,
            String schoolName,
            int academicYearId,
            String academicYearName,
            int termPeriodId,
            String termName,
            String gradeLevelName,
            String subjectName
    ) {
    }

    /** One student's totals on one skill, summed over every finalized assessment in the
     *  selected term, grade level and subject. possiblePoints only counts the assessments the
     *  student actually took, so a missed quiz does not count as zero. */
    public record StudentCompetencyTotalRow(
            long rootTagId,
            String rootTagName,
            long skillId,
            long competencyId,
            String competencyName,
            long studentId,
            String fullName,
            String sectionName,
            int assessmentCount,
            BigDecimal possiblePoints,
            BigDecimal earnedPoints
    ) {
    }
}
