package com.capstone.assessment.analytics.dto;

public record ItemAnalysisDto(
        Long testPartId,
        Integer itemNumber,
        String competencyName,
        Integer correctResponses,
        Integer totalResponses,
        Double difficulty,
        Double correctnessPercentage
) {
}
