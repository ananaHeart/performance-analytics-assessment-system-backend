package com.capstone.assessment.v3.assessment.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record V3AssessmentResponse(
        long testId,
        String testUuid,
        long testAssignmentId,
        String assignmentUuid,
        long classAssignmentId,
        int termPeriodId,
        String termName,
        String testName,
        String testType,
        String instructions,
        int totalItems,
        BigDecimal maximumScore,
        String status,
        int versionNumber,
        Instant openAt,
        Instant closeAt,
        String assignmentStatus,
        boolean allowLateCapture,
        boolean outsideScheduleConfirmed,
        String outsideScheduleReason,
        Long outsideScheduleConfirmedByUserId,
        Instant outsideScheduleConfirmedAt,
        AssignmentContext assignment,
        List<Part> parts,
        Instant createdAt,
        Instant updatedAt
) {
    public record AssignmentContext(
            long classId,
            int academicYearId,
            String academicYearName,
            int gradeLevelId,
            String gradeLevelName,
            String sectionName,
            int subjectId,
            String subjectName
    ) {
    }

    public record Part(
            long testPartId,
            int partOrder,
            String partName,
            String questionTypeCode,
            String questionTypeName,
            int numberOfItems,
            BigDecimal pointsPerItem,
            String partInstructions,
            List<Question> questions,
            List<SkillMapping> skillMappings
    ) {
    }

    public record Question(
            long questionId,
            String questionUuid,
            int itemNumber,
            String questionText,
            BigDecimal maximumPoints,
            String responseInstructions,
            Integer maximumResponseLength,
            Integer expectedResponseCount,
            String responseRegionSize,
            boolean forcePageBreakBefore,
            boolean answerOrderRequired,
            List<Option> options,
            AnswerKey answerKey,
            List<AcceptedAnswer> acceptedAnswers,
            Rubric rubric
    ) {
    }

    public record Option(
            long questionOptionId,
            String optionKey,
            String optionText,
            int optionOrder
    ) {
    }

    public record AnswerKey(
            String answerKeyType,
            String correctOptionKey,
            String scoringMethod,
            String answerExplanation
    ) {
    }

    public record AcceptedAnswer(
            long acceptedAnswerId,
            Integer answerOrder,
            String acceptedText,
            String matchingMode,
            boolean caseSensitive,
            BigDecimal points,
            boolean primary
    ) {
    }

    public record Rubric(
            long rubricId,
            String rubricUuid,
            String rubricName,
            String description,
            BigDecimal totalPoints,
            String status,
            List<RubricCriterion> criteria
    ) {
    }

    public record RubricCriterion(
            long rubricCriterionId,
            int criterionOrder,
            String criterionName,
            String criterionDescription,
            BigDecimal maximumPoints,
            JsonNode levelDefinition,
            boolean required
    ) {
    }

    public record SkillMapping(
            long partSkillMappingId,
            long skillId,
            String rootTagName,
            String competencyName,
            int startItemNumber,
            int endItemNumber,
            int itemCount
    ) {
    }
}
