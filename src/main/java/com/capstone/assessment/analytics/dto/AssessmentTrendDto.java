package com.capstone.assessment.analytics.dto;

import java.time.LocalDate;

public record AssessmentTrendDto(
        Long testId,
        String testName,
        LocalDate testDate,
        Double averageScore
) {
}
