package com.capstone.assessment.sync.dto;

import java.time.LocalDateTime;

public record TestResultSyncDto(
        Long testResultId,
        Long testId,
        Long studentId,
        Integer totalScore,
        String rawAnswers,
        LocalDateTime checkedAt
) {
}
