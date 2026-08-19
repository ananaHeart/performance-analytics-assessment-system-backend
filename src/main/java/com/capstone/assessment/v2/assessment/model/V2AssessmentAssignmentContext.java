package com.capstone.assessment.v2.assessment.model;

public record V2AssessmentAssignmentContext(
        Long classAssignmentId,
        Long classId,
        Long teacherUserId,
        String schoolId,
        Integer subjectId,
        String subjectName,
        String assignmentStatus,
        String assignmentRole,
        Integer academicYearId,
        String yearName,
        Integer gradeLevelId,
        String gradeLevelName,
        Integer sectionId,
        String sectionName,
        String classStatus
) {
}
