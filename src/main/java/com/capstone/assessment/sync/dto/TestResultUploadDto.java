package com.capstone.assessment.sync.dto;

import java.time.OffsetDateTime;

public record TestResultUploadDto(
        String localResultId,
        Long testId,
        Long studentId,
        Integer totalScore,
        Integer maxScore,
        String rawAnswers,
        OffsetDateTime checkedAt
) {
}
