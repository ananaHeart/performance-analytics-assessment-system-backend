package com.capstone.assessment.sync.dto;

public record ItemResponseUploadDto(
        String localResponseId,
        String localResultId,
        Long testPartId,
        Integer itemNumber,
        Boolean isCorrect
) {
}
