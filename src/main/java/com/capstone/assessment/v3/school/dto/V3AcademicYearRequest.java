package com.capstone.assessment.v3.school.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record V3AcademicYearRequest(
        @NotNull @Positive Integer curriculumId,
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{4}") String yearName,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @Size(min = 4, max = 4) List<@Valid V3TermPeriodRequest> termPeriods
) {
}
