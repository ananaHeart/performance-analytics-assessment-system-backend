package com.capstone.assessment.v3.school.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record V3SchoolProfileUpdateRequest(
        @NotBlank @Size(max = 120) String schoolName,
        @Size(max = 20) String contactNumber,
        @Size(max = 120) String email,
        @NotNull @Valid Address address
) {
    public record Address(
            @Size(max = 20) String regionCode,
            @Size(max = 100) String regionName,
            @Size(max = 20) String provinceCode,
            @Size(max = 100) String provinceName,
            @Size(max = 20) String cityMunicipalityCode,
            @Size(max = 120) String cityMunicipalityName,
            @Size(max = 20) String barangayCode,
            @Size(max = 120) String barangayName,
            @Size(max = 255) String addressLine,
            @Size(max = 10) String postalCode,
            @NotBlank String addressSource
    ) {
    }
}
