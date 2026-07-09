package com.capstone.assessment.importexport.dto;

public record ManualStudentResponse(
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
