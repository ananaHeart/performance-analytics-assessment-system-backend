package com.capstone.assessment.v3.assessment.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record V3RubricCriterionRequest(
        @NotNull @Positive Integer criterionOrder,
        @NotBlank @Size(max = 120) String criterionName,
        @NotBlank String criterionDescription,
        @NotNull @DecimalMin(value = "0.01") BigDecimal maximumPoints,
        JsonNode levelDefinition,
        Boolean required
) {
}
