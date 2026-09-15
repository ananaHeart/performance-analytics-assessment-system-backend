package com.capstone.assessment.v3.assessment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record V3AssessmentPartRequest(
        @NotNull @Positive Integer partOrder,
        @NotBlank @Size(max = 80) String partName,
        @NotBlank @Size(max = 40) String questionTypeCode,
        @NotNull @Positive Integer numberOfItems,
        @NotNull @DecimalMin(value = "0.01") BigDecimal pointsPerItem,
        String partInstructions,
        @NotEmpty List<@Valid V3AssessmentQuestionRequest> questions,
        @NotEmpty List<@Valid V3PartSkillMappingRequest> skillMappings
) {
}
