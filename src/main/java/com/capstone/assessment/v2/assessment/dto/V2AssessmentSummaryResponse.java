package com.capstone.assessment.v2.assessment.dto;

import java.time.Instant;
import java.time.LocalDate;

public record V2AssessmentSummaryResponse(
        Long testId,
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
        Integer termPeriodId,
        String termName,
        String testName,
        String testType,
        LocalDate testDate,
        Integer totalItems,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
