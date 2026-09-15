package com.capstone.assessment.v3.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record V3QuestionOptionRequest(
        @NotBlank @Size(max = 10) String optionKey,
        @NotBlank String optionText,
        @NotNull @Positive Integer optionOrder
) {
}
