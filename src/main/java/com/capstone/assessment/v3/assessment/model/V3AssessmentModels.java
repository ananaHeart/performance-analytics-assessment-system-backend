package com.capstone.assessment.v3.assessment.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public final class V3AssessmentModels {

    private V3AssessmentModels() {
    }

    public record AssignmentContext(
            long classAssignmentId,
            long teacherUserId,
            String schoolId,
            long classId,
            int academicYearId,
            String academicYearName,
            int gradeLevelId,
            String gradeLevelName,
            String sectionName,
            int subjectId,
            String subjectName,
            String assignmentRole,
            String assignmentStatus,
            String classStatus
    ) {
    }

    public record QuestionType(
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
            boolean allowsTeacherAnswerEdit,
            boolean active
    ) {
    }

    public record AssessmentHeader(
            long testId,
            String testUuid,
            String schoolId,
            long createdByUserId,
            int versionNumber,
            int termPeriodId,
            String termName,
            String testName,
            String testType,
            String instructions,
            int totalItems,
            String status,
            long testAssignmentId,
            String assignmentUuid,
            long classAssignmentId,
            Instant openAt,
            Instant closeAt,
            String assignmentStatus,
            boolean allowLateCapture,
            boolean outsideScheduleConfirmed,
            String outsideScheduleReason,
            Long outsideScheduleConfirmedByUserId,
            Instant outsideScheduleConfirmedAt,
            long classId,
            int academicYearId,
            String academicYearName,
            int gradeLevelId,
            String gradeLevelName,
            String sectionName,
            int subjectId,
            String subjectName,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record TermWindow(
            int termPeriodId,
            int academicYearId,
            Instant startAt,
            Instant endAt,
            String status
    ) {
    }

    public record ClassScheduleWindow(
            int dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            String timezoneName,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {
    }

    public record PartRow(
            long testPartId,
            long testId,
            int partOrder,
            String partName,
            int questionTypeId,
            String questionTypeCode,
            String questionTypeName,
            int numberOfItems,
            BigDecimal pointsPerItem,
            String partInstructions
    ) {
    }

    public record QuestionRow(
            long questionId,
            String questionUuid,
            long testPartId,
            int itemNumber,
            String questionText,
            BigDecimal maximumPoints,
            Long rubricId,
            String responseInstructions,
            boolean answerOrderRequired,
            Integer maximumResponseLength,
            Integer expectedResponseCount,
            String responseRegionSize,
            boolean forcePageBreakBefore
    ) {
    }

    public record OptionRow(
            long questionOptionId,
            long questionId,
            String optionKey,
            String optionText,
            int optionOrder
    ) {
    }

    public record AnswerKeyRow(
            long questionId,
            String answerKeyType,
            Long correctQuestionOptionId,
            String correctOptionKey,
            String scoringMethod,
            Long rubricId,
            String answerExplanation
    ) {
    }

    public record AcceptedAnswerRow(
            long acceptedAnswerId,
            long questionId,
            Integer answerOrder,
            String acceptedText,
            String normalizedText,
            String matchingMode,
            boolean caseSensitive,
            BigDecimal points,
            boolean primary
    ) {
    }

    public record SkillMappingRow(
            long partSkillMappingId,
            long testPartId,
            long skillId,
            String rootTagName,
            String competencyName,
            int startItemNumber,
            int endItemNumber,
            int itemCount
    ) {
    }

    public record RubricRow(
            long rubricId,
            String rubricUuid,
            String schoolId,
            long createdByUserId,
            String rubricName,
            String description,
            BigDecimal totalPoints,
            String status
    ) {
    }

    public record RubricCriterionRow(
            long rubricCriterionId,
            long rubricId,
            int criterionOrder,
            String criterionName,
            String criterionDescription,
            BigDecimal maximumPoints,
            JsonNode levelDefinition,
            boolean required
    ) {
    }
}
