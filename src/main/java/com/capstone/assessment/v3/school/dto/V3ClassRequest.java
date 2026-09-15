package com.capstone.assessment.v3.school.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record V3ClassRequest(
        @NotNull @Positive Integer academicYearId,
        @NotNull @Positive Integer gradeLevelId,
        @NotBlank @Size(max = 50) String sectionName
) {
}
