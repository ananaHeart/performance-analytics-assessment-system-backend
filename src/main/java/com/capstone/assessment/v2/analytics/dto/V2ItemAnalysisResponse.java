package com.capstone.assessment.v2.analytics.dto;

import java.math.BigDecimal;

public record V2ItemAnalysisResponse(
        Long questionId,
        Long testPartId,
        Integer itemNumber,
        Long skillId,
        Long competencyId,
        String competencyName,
        Integer correctResponses,
        Integer totalResponses,
        BigDecimal correctnessPercentage,
        String difficultyLevel
) {
}
