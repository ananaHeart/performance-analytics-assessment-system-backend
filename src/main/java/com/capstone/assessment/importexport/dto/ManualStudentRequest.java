package com.capstone.assessment.importexport.dto;

public record ManualStudentRequest(
        String studentLrn,
        String firstName,
        String lastName,
        String gender,
        Long sectionId,
        Long academicYearId
) {
}
