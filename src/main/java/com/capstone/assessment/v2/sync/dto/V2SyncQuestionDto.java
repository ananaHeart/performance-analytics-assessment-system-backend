package com.capstone.assessment.v2.sync.dto;

public record V2SyncQuestionDto(
        Long questionId,
        Long testPartId,
        Integer itemNumber,
        String questionText,
        String optionA,
        String optionB,
        String optionC,
        String optionD,
        String optionE
) {
}
