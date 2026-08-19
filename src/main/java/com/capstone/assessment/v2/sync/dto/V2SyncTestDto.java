package com.capstone.assessment.v2.sync.dto;

import java.time.LocalDate;

public record V2SyncTestDto(
        Long testId,
        Long classAssignmentId,
        Integer termPeriodId,
        String termName,
        String testName,
        String testType,
        LocalDate testDate,
        String instructions,
        Integer totalItems,
        String status
) {
}
