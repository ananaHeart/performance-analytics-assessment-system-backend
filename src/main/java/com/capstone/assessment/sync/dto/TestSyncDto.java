package com.capstone.assessment.sync.dto;

import java.time.LocalDate;

public record TestSyncDto(
        Long testId,
        Long classId,
        String testName,
        String testType,
        LocalDate testDate,
        String testStatus
) {
}
