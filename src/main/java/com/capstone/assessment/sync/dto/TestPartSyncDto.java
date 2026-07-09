package com.capstone.assessment.sync.dto;

public record TestPartSyncDto(
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
