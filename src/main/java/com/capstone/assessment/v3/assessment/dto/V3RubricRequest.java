package com.capstone.assessment.v3.assessment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record V3RubricRequest(
        @NotBlank @Size(max = 120) String rubricName,
        String description,
        @NotEmpty List<@Valid V3RubricCriterionRequest> criteria
) {
}
