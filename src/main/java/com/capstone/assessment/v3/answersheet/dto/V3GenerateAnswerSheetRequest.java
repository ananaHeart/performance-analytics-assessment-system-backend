package com.capstone.assessment.v3.answersheet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record V3GenerateAnswerSheetRequest(
        @NotBlank(message = "Paper size is required.")
        @Pattern(
                regexp = "A4|US_LETTER|US_LEGAL",
                message = "Paper size must be A4, US_LETTER, or US_LEGAL."
        )
        String paperSizeCode
) {
}
