package com.capstone.assessment.v2.analytics.dto;

import java.math.BigDecimal;

public record V2GradeMasteryResponse(
        long gradeLevelId,
        String gradeLevelName,
        BigDecimal earnedPoints,
        BigDecimal possiblePoints,
        BigDecimal masteryRate,
        int respondentCount
) {
}
