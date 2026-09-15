package com.capstone.assessment.v3.school.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record V3TermPeriodRequest(
        @NotBlank @Size(max = 50) String termName,
        @Min(1) @Max(4) int termOrder,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        @NotBlank @Size(max = 20) String activationMode
) {
}
