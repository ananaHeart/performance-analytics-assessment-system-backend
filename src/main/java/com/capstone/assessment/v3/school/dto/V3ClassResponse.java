package com.capstone.assessment.v3.school.dto;

public record V3ClassResponse(
        long classId,
        int academicYearId,
        String academicYearName,
        int sectionId,
        int gradeLevelId,
        String gradeLevelName,
        String sectionName,
        String status,
        int enrolledStudentCount,
        boolean created
) {
}
