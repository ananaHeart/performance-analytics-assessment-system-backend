package com.capstone.assessment.sync.dto;

public record ItemResponseSyncDto(
        Long itemResultId,
        Long testResultId,
        Long testPartId,
        Integer itemNumber,
        Boolean isCorrect
) {
}
