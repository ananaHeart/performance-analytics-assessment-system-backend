package com.capstone.assessment.sync.dto;

import java.time.OffsetDateTime;

public record ItemResponseUploadDto(
        String localResponseId,
        String localResultId,
        Long testPartId,
        Integer itemNumber,
        Boolean isCorrect,
        OffsetDateTime updatedAt
) {
}
