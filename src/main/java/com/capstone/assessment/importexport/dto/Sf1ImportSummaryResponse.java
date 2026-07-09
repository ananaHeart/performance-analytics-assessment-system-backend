package com.capstone.assessment.importexport.dto;

public record Sf1ImportSummaryResponse(
        String detectedSchoolYear,
        String detectedSectionName,
        Integer importedStudents,
        Integer updatedStudents,
        Integer enrolledStudents,
        Integer skippedRows,
        String message
) {
}
