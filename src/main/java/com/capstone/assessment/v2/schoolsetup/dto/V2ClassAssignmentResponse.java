package com.capstone.assessment.v2.schoolsetup.dto;

import java.time.Instant;

public record V2ClassAssignmentResponse(
        Long classAssignmentId,
        Long classId,
        Integer academicYearId,
        String academicYearName,
        Integer gradeLevelId,
        String gradeLevelName,
        Integer sectionId,
        String sectionName,
        Long teacherUserId,
        String teacherName,
        Integer subjectId,
        String subjectName,
        String assignmentRole,
        String status,
        Instant assignedAt
) {
}
