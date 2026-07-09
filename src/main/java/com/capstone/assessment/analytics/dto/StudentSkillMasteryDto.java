package com.capstone.assessment.analytics.dto;

public record StudentSkillMasteryDto(
        Long studentId,
        Long competencyId,
        String competencyName,
        Integer earnedPoints,
        Integer totalPoints,
        Double masteryRate,
        String status,
        Integer assessmentsCount
) {
}
