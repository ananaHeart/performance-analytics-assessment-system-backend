package com.capstone.assessment.v3.school.dto;

import java.time.Instant;

public record V3ClassAssignmentResponse(
        long classAssignmentId,
        long classId,
        long teacherUserId,
        String teacherName,
        int subjectId,
        String subjectName,
        int academicYearId,
        String academicYearName,
        int gradeLevelId,
        String gradeLevelName,
        int sectionId,
        String sectionName,
        String assignmentRole,
        String status,
        Instant assignedAt,
        Instant endedAt,
        String statusReason,
        boolean created
) {
}
