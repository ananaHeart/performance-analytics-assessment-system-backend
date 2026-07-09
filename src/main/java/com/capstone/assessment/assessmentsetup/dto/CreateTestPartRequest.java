package com.capstone.assessment.assessmentsetup.dto;

public record CreateTestPartRequest(
        Long competencyId,
        String partOrder,
        String partType,
        Integer numberOfItems,
        Integer pointsPerItem,
        String answerKey
) {
}
