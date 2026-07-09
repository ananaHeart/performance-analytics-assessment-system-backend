package com.capstone.assessment.schoolsetup.dto;

public record StudentDto(
        Long studentId,
        String studentLrn,
        String firstName,
        String lastName,
        String gender,
        Long sectionId,
        String sectionName,
        Long gradeLevelId,
        String gradeLevelName
) {
}
