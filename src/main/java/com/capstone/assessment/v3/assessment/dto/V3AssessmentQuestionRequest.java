package com.capstone.assessment.v3.assessment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record V3AssessmentQuestionRequest(
        @NotNull @Positive Integer itemNumber,
        @NotBlank String questionText,
        @DecimalMin(value = "0.01") BigDecimal maximumPoints,
        String responseInstructions,
        @Positive Integer maximumResponseLength,
        @Positive Integer expectedResponseCount,
        @Size(max = 20) String responseRegionSize,
        Boolean forcePageBreakBefore,
        List<@Valid V3QuestionOptionRequest> options,
        @Size(max = 10) String correctOptionKey,
        String answerExplanation,
        @Size(max = 20) String matchingMode,
        List<@Valid V3AcceptedAnswerRequest> acceptedAnswers,
        @Positive Long rubricId,
        @Valid V3RubricRequest rubric
) {
}
