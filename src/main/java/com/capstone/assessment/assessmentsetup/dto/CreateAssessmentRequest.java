package com.capstone.assessment.assessmentsetup.dto;

import java.time.LocalDate;

public record CreateAssessmentRequest(
        Long classId,
        String testName,
        String testType,
        LocalDate testDate,
        Long gradingPeriodId,
        String testStatus
) {
}
