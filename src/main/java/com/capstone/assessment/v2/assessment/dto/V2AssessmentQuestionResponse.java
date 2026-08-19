package com.capstone.assessment.v2.assessment.dto;

import java.util.List;

public record V2AssessmentQuestionResponse(
        Long questionId,
        Integer itemNumber,
        String questionText,
        String optionA,
        String optionB,
        String optionC,
        String optionD,
        String optionE,
        String correctOption,
        List<Long> skillIds
) {
}
