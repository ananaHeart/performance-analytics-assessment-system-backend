package com.capstone.assessment.v2.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record V2AssessmentQuestionRequest(
        @NotNull @Positive Integer itemNumber,
        @NotBlank String questionText,
        @Size(max = 255) String optionA,
        @Size(max = 255) String optionB,
        @Size(max = 255) String optionC,
        @Size(max = 255) String optionD,
        @Size(max = 255) String optionE,
        @NotBlank String correctOption,
        List<@NotNull @Positive Long> skillIds
) {
}
