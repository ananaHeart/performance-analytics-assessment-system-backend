package com.capstone.assessment.assessmentsetup.dto;

public record TestPartDetailDto(
        Long testPartId,
        Long testId,
        Long competencyId,
        String competencyName,
        String partOrder,
        String partType,
        Integer numberOfItems,
        Integer pointsPerItem,
        String answerKey
) {
}
