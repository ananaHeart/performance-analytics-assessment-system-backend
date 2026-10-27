package com.capstone.assessment.v3.account.dto;

import com.capstone.assessment.v3.auth.dto.V3TeacherRegistrationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * TCH-4: the principal corrects a teacher's details. Email, contact number and password stay
 * with the teacher (they need a verification code), so they are not here.
 */
public record V3TeacherUpdateRequest(
        @NotBlank @Size(max = 50) String firstName,
        @Size(max = 50) String middleName,
        @NotBlank @Size(max = 50) String lastName,
        @Positive Integer suffixId,
        @NotNull @Positive Integer genderId,
        @NotNull LocalDate birthDate,
        @NotNull @Min(1) @Max(12) Integer teachingStartMonth,
        @NotNull @Min(1900) @Max(2200) Integer teachingStartYear,
        @NotNull @Positive Integer majorId,
        @NotNull @Positive Integer educationalAttainmentId,
        @NotNull @Valid V3TeacherRegistrationRequest.Address address,
        @Size(max = 255) String reason
) {
}
