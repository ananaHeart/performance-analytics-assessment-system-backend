package com.capstone.assessment.v2.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record V2AddressRequest(
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
