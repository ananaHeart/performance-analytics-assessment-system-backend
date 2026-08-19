package com.capstone.assessment.v2.assessment.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record V2SkillRangeMappingRequest(
        @NotNull @Positive Integer fromItemNumber,
        @NotNull @Positive Integer toItemNumber,
        @NotEmpty List<@NotNull @Positive Long> skillIds
) {
}
