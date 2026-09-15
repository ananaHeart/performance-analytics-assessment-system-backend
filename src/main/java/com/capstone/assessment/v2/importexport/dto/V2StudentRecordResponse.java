package com.capstone.assessment.v2.importexport.dto;

public record V2StudentRecordResponse(
        Long studentId,
        Long classListId,
        Long classId,
        String studentLrn,
        String firstName,
        String middleName,
        String lastName,
        String gender,
        Integer sectionId,
        String sectionName,
        String gradeLevelName,
        Integer academicYearId,
        String academicYear
) {
}
