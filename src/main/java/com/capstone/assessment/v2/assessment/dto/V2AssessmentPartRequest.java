package com.capstone.assessment.v2.assessment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record V2AssessmentPartRequest(
        @NotNull @Positive Integer partOrder,
        @NotBlank @Size(max = 80) String partName,
        @NotBlank @Size(max = 30) String partType,
        @NotNull @DecimalMin(value = "0.01") BigDecimal pointsPerItem,
        @NotEmpty List<@Valid V2AssessmentQuestionRequest> questions,
        List<@Valid V2SkillRangeMappingRequest> skillMappings
) {
}
