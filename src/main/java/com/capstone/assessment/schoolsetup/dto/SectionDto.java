package com.capstone.assessment.schoolsetup.dto;

public record SectionDto(
        Long sectionId,
        Long gradeLevelId,
        String gradeLevelName,
        String sectionName
) {
}
