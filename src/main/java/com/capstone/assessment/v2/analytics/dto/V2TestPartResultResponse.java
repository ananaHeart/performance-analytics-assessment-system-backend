package com.capstone.assessment.v2.analytics.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record V2TestPartResultResponse(
        Long studentId,
        String studentName,
        String studentLrn,
        Long testId,
        Long testPartId,
        BigDecimal partScore,
        BigDecimal maxScore,
        BigDecimal percentage,
        String performance,
        Instant checkedAt,
        Instant syncedAt
) {
}
