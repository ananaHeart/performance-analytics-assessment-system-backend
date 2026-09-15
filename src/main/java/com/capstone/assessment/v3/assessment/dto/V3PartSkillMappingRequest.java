package com.capstone.assessment.v3.assessment.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record V3PartSkillMappingRequest(
        @NotNull @Positive Integer startItemNumber,
        @NotNull @Positive Integer endItemNumber,
        @NotEmpty List<@NotNull @Positive Long> skillIds
) {
}
