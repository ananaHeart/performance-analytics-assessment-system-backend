package com.capstone.assessment.v2.assessment.model;

import java.util.List;

public record V2AssessmentQuestionRow(
        Long questionId,
        Long testPartId,
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
