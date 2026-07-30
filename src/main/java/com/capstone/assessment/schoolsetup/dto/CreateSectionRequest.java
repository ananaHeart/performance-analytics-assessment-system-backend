package com.capstone.assessment.schoolsetup.dto;

public record CreateSectionRequest(
        Long gradeLevelId,
        Long academicYearId,
        String sectionName
) {
}
