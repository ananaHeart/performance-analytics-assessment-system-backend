package com.capstone.assessment.v2.analytics.dto;

import java.math.BigDecimal;

public record V2LmsResponse(
        Long skillId,
        Long competencyId,
        String competencyName,
        BigDecimal earnedPoints,
        BigDecimal possiblePoints,
        BigDecimal masteryRate,
        String status,
        Integer respondentCount,
        Integer affectedStudents
) {
}
