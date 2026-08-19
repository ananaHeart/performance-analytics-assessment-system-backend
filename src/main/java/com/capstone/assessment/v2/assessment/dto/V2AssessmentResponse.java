package com.capstone.assessment.v2.assessment.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record V2AssessmentResponse(
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
        String instructions,
        Integer totalItems,
        String status,
        Instant createdAt,
        Instant updatedAt,
        List<V2AssessmentPartResponse> parts
) {
}
