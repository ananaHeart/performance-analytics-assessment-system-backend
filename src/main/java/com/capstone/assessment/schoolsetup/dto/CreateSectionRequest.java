package com.capstone.assessment.schoolsetup.dto;

public record CreateSectionRequest(
        Long gradeLevelId,
        String sectionName
) {
}
