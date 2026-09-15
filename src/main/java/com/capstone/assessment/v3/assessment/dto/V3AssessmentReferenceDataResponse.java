package com.capstone.assessment.v3.assessment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record V3AssessmentReferenceDataResponse(
        List<AssignmentOption> assignments,
        AssignmentOption selectedAssignment,
        List<TermPeriodOption> termPeriods,
        List<QuestionTypeOption> questionTypes,
        List<SkillOption> skills,
        List<RubricOption> rubrics,
        List<String> testTypes,
        List<String> responseRegionSizes
) {
    public record AssignmentOption(
            long classAssignmentId,
            long classId,
            int academicYearId,
            String academicYearName,
            int gradeLevelId,
            String gradeLevelName,
            String sectionName,
            int subjectId,
            String subjectName,
            String assignmentRole,
            String status
    ) {
    }

    public record TermPeriodOption(
            int termPeriodId,
            String termName,
            int termOrder,
            Instant startAt,
            Instant endAt,
            String status
    ) {
    }

    public record QuestionTypeOption(
            int questionTypeId,
            String code,
            String name,
            String captureMode,
            String scoringMode,
            boolean supportsOmr,
            boolean supportsOcr,
            boolean supportsMultipleResponse,
            boolean requiresAttachment,
            boolean requiresTeacherVerification,
            boolean allowsTeacherAnswerEdit
    ) {
    }

    public record SkillOption(
            long skillId,
            long competencyId,
            int rootTagId,
            String rootTagName,
            String competencyName,
            int termPeriodId,
            int gradeLevelId,
            int subjectId
    ) {
    }

    public record RubricOption(
            long rubricId,
            String rubricName,
            BigDecimal totalPoints,
            String status,
            long createdByUserId
    ) {
    }
}
