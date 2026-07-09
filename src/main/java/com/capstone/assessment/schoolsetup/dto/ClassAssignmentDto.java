package com.capstone.assessment.schoolsetup.dto;

public record ClassAssignmentDto(
        Long classId,
        Long teacherId,
        String teacherName,
        Long subjectId,
        String subjectName,
        Long sectionId,
        String sectionName,
        Long gradeLevelId,
        String gradeLevelName,
        String academicYear
) {
}
