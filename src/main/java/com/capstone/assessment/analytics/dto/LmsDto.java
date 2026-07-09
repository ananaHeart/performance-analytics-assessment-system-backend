package com.capstone.assessment.analytics.dto;

public record LmsDto(
        Long competencyId,
        String competencyName,
        Double masteryRate,
        String status
) {
}
