package com.capstone.assessment.sync.dto;

public record TestResultUploadDto(
        String localResultId,
        Long testId,
        Long studentId,
        Integer totalScore,
        String rawAnswers
) {
}
