package com.capstone.assessment.v3.school.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** SET-4 (D8): the principal extends or corrects a term's dates; a reason is always required. */
public record V3TermDatesRequest(
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        @NotBlank @Size(min = 5, max = 255) String reason
) {
}
