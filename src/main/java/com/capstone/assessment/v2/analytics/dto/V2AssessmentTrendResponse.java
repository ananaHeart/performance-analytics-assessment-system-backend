package com.capstone.assessment.v2.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record V2AssessmentTrendResponse(
        long testId,
        String testName,
        LocalDate testDate,
        BigDecimal masteryRate,
        int respondentCount
) {
}
