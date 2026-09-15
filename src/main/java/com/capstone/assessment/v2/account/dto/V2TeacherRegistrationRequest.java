package com.capstone.assessment.v2.account.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record V2TeacherRegistrationRequest(
        @NotBlank @Size(max = 20) String schoolCode,
        @NotBlank
        @Pattern(
                regexp = "(?i)^(email|sms)$",
                message = "Verification method must be email or sms."
        )
        String verificationMethod,
        @NotBlank @Size(max = 50) String firstName,
        @Size(max = 50) String middleName,
        @NotBlank @Size(max = 50) String lastName,
        @Size(max = 10) String suffix,
        @Positive Integer suffixId,
        @NotNull @Past(message = "Birth date must be before today.") LocalDate birthDate,
        @Past(message = "Teaching start date must be before today.") LocalDate teachingStartDate,
        @NotBlank @Email @Size(max = 120) String email,
        @NotBlank @Size(max = 20) String contactNumber,
        @NotBlank @Size(min = 10, max = 128) String password,
        @NotNull @Positive Integer genderId,
        @NotNull @Positive Integer majorId,
        @NotNull @Positive Integer educationalAttainmentId,
        @NotNull @Valid V2AddressRequest address
) {
}
