package com.capstone.assessment.analytics.dto;

public record InterventionStudentDto(
        Long studentId,
        String studentName,
        Integer score,
        Integer totalItems,
        Double percentage
) {
}
