package com.capstone.assessment.v3.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record V3TeacherRegistrationRequest(
        @NotBlank @Size(max = 20) String schoolCode,
        @NotBlank @Pattern(regexp = "(?i)^email$", message = "Email is the only available verification method.")
        String verificationMethod,
        @NotBlank @Size(max = 50) String firstName,
        @Size(max = 50) String middleName,
        @NotBlank @Size(max = 50) String lastName,
        @Positive Integer suffixId,
        @NotNull @Past(message = "Birth date must be before today.") LocalDate birthDate,
        @Min(1) @Max(12) Integer teachingStartMonth,
        @Min(1900) @Max(2200) Integer teachingStartYear,
        @NotBlank @Email @Size(max = 120) String email,
        @NotBlank @Size(max = 20) String contactNumber,
        @NotBlank @Size(min = 10, max = 128) String password,
        @NotNull @Positive Integer genderId,
        @NotNull @Positive Integer majorId,
        @NotNull @Positive Integer educationalAttainmentId,
        @NotNull @Valid Address address
) {
    public record Address(
            @Pattern(regexp = "[A-Za-z]{2}", message = "countryCode must contain two letters")
            String countryCode,
            @Size(max = 20) String regionCode,
            @Size(max = 100) String regionName,
            @Size(max = 20) String provinceCode,
            @Size(max = 100) String provinceName,
            @Size(max = 20) String cityMunicipalityCode,
            @NotBlank @Size(max = 120) String cityMunicipalityName,
            @Size(max = 20) String barangayCode,
            @NotBlank @Size(max = 120) String barangayName,
            @NotBlank @Size(max = 255) String addressLine,
            @Size(max = 10) String postalCode
    ) {
    }
}
