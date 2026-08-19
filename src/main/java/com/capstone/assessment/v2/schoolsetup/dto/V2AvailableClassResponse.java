package com.capstone.assessment.v2.schoolsetup.dto;

public record V2AvailableClassResponse(
        Long classId,
        Integer academicYearId,
        String academicYearName,
        Integer gradeLevelId,
        String gradeLevelName,
        Integer sectionId,
        String sectionName,
        Integer enrolledStudents
) {
}
