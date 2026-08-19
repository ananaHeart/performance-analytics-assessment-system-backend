package com.capstone.assessment.v2.assessment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record V2AssessmentRequest(
        @NotNull @Positive Long classAssignmentId,
        @NotNull @Positive Integer termPeriodId,
        @NotBlank @Size(max = 120) String testName,
        @NotBlank @Size(max = 30) String testType,
        @NotNull LocalDate testDate,
        String instructions,
        @NotEmpty List<@Valid V2AssessmentPartRequest> parts
) {
}
