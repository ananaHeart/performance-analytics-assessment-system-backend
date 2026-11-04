package com.capstone.assessment.v3.school.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** R2-4: the principal corrects or extends the school year's own dates; a reason is always required. */
public record V3AcademicYearDatesRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotBlank @Size(min = 5, max = 255) String reason
) {
}
