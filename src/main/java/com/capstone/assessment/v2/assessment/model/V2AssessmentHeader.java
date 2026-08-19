package com.capstone.assessment.v2.assessment.model;

import java.time.Instant;
import java.time.LocalDate;

public record V2AssessmentHeader(
        Long testId,
        Long classAssignmentId,
        Long classId,
        Long teacherUserId,
        String schoolId,
        Integer academicYearId,
        String yearName,
        Integer gradeLevelId,
        String gradeLevelName,
        Integer sectionId,
        String sectionName,
        Integer subjectId,
        String subjectName,
        Integer termPeriodId,
        String termName,
        String testName,
        String testType,
        LocalDate testDate,
        String instructions,
        Integer totalItems,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
