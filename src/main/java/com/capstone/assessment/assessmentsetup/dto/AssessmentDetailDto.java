package com.capstone.assessment.assessmentsetup.dto;

import java.time.LocalDate;
import java.util.List;

public record AssessmentDetailDto(
        Long testId,
        Long classId,
        String testName,
        String testType,
        LocalDate testDate,
        Long gradingPeriodId,
        String gradingPeriodName,
        String testStatus,
        String subjectName,
        String sectionName,
        String gradeLevelName,
        List<TestPartDetailDto> testParts
) {
}
