package com.capstone.assessment.v3.scoring.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record V3ScoredResultResponse(
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
        BigDecimal percentage,
        String performanceStatus,
        String performanceLabel,
        long performanceRuleSetId,
        int itemsEvaluated,
        int scoreVersion,
        String resultStatus,
        Instant scoredAt,
        boolean scoreChanged,
        List<PartScore> parts
) {
    public record PartScore(
            long testPartId,
            int partOrder,
            String partName,
            BigDecimal totalScore,
            BigDecimal maxScore,
            BigDecimal percentage,
            int itemsEvaluated
    ) {
    }
}
