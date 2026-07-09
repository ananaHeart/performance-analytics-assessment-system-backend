package com.capstone.assessment.assessmentsetup.dto;

public record CompetencyOptionDto(
        Long competencyId,
        Long gradeLevelId,
        Long subjectId,
        String competencyName
) {
}
