package com.capstone.assessment.v3.scoring.model;

import java.math.BigDecimal;
import java.time.Instant;

public final class V3ScoringModels {

    private V3ScoringModels() {
    }

    public record ResultContext(
            long testResultId,
            String resultUuid,
            long testAssignmentId,
            long testId,
            long classListId,
            long studentId,
            String studentName,
            int attemptNumber,
            BigDecimal totalScore,
            BigDecimal maxScore,
            int itemsEvaluated,
            String resultStatus,
            BigDecimal percentageSnapshot,
            String performanceStatus,
            Long performanceRuleSetId,
            int scoreVersion,
            Instant scoredAt,
            long teacherUserId,
            String testSchoolId,
            String studentSchoolId,
            long assignmentClassId,
            long membershipClassId,
            String testStatus,
            String testAssignmentStatus
    ) {
    }

    public record ScoringRow(
            long testPartId,
            int partOrder,
            String partName,
            long questionId,
            int itemNumber,
            String questionTypeCode,
            BigDecimal maximumPoints,
            Long questionRubricId,
            Long answerKeyId,
            String answerKeyType,
            Long correctQuestionOptionId,
            Long answerKeyRubricId,
            Long studentAnswerId,
            String answerUuid,
            Long selectedQuestionOptionId,
            Long selectedOptionQuestionId,
            String responseText,
            long writtenEvidenceAttachmentCount,
            String answerStatus,
            String evaluationStatus,
            Long verifiedByUserId,
            Instant verifiedAt,
            Boolean isCorrect,
            BigDecimal pointsEarned,
            Instant finalizedAt,
            int answerScoreVersion
    ) {
    }

    public record RubricScoreSummary(
            long totalCriteria,
            long requiredCriteria,
            long scoredCriteria,
            long scoredRequiredCriteria,
            long excessiveScoreCount,
            BigDecimal awardedPoints
    ) {
    }

    public record PerformanceRuleSet(
            long performanceRuleSetId,
            String ruleDefinition
    ) {
    }
}
