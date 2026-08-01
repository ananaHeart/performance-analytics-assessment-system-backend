package com.capstone.assessment.analytics.dto;

import java.time.Instant;

public record TestPartResultDto(
        Long studentId,
        String studentName,
        String studentLrn,
        Long testId,
        Long testPartId,
        Integer partScore,
        Integer maxScore,
        Double percentage,
        String performance,
        Instant checkedAt,
        Instant syncedAt
) {
}
