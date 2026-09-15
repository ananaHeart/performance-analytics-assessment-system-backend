package com.capstone.assessment.v2.importexport.dto;

public record V2Sf1ImportSummaryResponse(
        String detectedSchoolYear,
        String detectedSectionName,
        Integer academicYearId,
        Integer gradeLevelId,
        Integer sectionId,
        Long classId,
        Integer importedStudents,
        Integer updatedStudents,
        Integer enrolledStudents,
        Integer skippedRows,
        String message
) {
}
