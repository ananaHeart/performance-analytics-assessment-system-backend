package com.capstone.assessment.schoolsetup.dto;

public record SectionDto(
        Long sectionId,
        Long gradeLevelId,
        String gradeLevelName,
        Long academicYearId,
        String academicYear,
        String sectionName
) {
}
