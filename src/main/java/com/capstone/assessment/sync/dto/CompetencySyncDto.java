package com.capstone.assessment.sync.dto;

public record CompetencySyncDto(
        Long competencyId,
        Long gradeLevelId,
        Long subjectId,
        String competencyName
) {
}
