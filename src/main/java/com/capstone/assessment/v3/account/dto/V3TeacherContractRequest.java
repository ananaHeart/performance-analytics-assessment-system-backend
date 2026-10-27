package com.capstone.assessment.v3.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** REG-2: the principal extends (or corrects) the last day of the teacher's contract. */
public record V3TeacherContractRequest(
        @NotNull(message = "The contract end date is required.") LocalDate contractEndDate,
        @NotBlank(message = "A reason is required.")
        @Size(min = 5, max = 255, message = "The reason must contain 5 to 255 characters.")
        String reason
) {
}
