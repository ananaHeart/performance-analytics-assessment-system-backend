package com.capstone.assessment.sync.dto;

public record StudentSyncDto(
        Long studentId,
        String studentLrn,
        String firstName,
        String lastName,
        String gender,
        Long sectionId,
        String sectionName,
        Long gradeLevelId,
        String gradeLevelName,
        Long academicYearId,
        String academicYear
) {
}
