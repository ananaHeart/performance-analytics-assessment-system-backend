package com.capstone.assessment.v2.schoolsetup.model;

public record V2ClassContext(
        Long classId,
        Integer academicYearId,
        String academicYearName,
        Integer gradeLevelId,
        String gradeLevelName,
        Integer sectionId,
        String sectionName,
        String classStatus
) {
}
