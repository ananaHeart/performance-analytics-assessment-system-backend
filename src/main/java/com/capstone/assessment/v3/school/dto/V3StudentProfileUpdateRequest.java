package com.capstone.assessment.v3.school.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record V3StudentProfileUpdateRequest(
        @NotBlank @Size(max = 50) String firstName,
        @Size(max = 50) String middleName,
        @NotBlank @Size(max = 50) String lastName,
        @Positive Integer suffixId,
        @NotNull @Positive Integer genderId,
        @Past LocalDate birthDate,
        @NotBlank @Size(min = 5, max = 255) String reason
) {
}
