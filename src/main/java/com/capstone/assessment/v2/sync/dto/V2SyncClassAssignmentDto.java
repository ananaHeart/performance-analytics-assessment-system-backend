package com.capstone.assessment.v2.sync.dto;

public record V2SyncClassAssignmentDto(
        Long classAssignmentId,
        Long classId,
        Integer academicYearId,
        String yearName,
        Integer gradeLevelId,
        String gradeLevelName,
        Integer sectionId,
        String sectionName,
        Integer subjectId,
        String subjectName,
        String assignmentRole,
        String assignmentStatus
) {
}
