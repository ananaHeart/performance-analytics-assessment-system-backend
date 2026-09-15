package com.capstone.assessment.v3.assessment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record V3AcceptedAnswerRequest(
        @Positive Integer answerOrder,
        @NotBlank String acceptedText,
        @DecimalMin(value = "0.00") BigDecimal points,
        Boolean primary,
        Boolean caseSensitive
) {
}
