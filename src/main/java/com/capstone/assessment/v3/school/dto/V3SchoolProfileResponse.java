package com.capstone.assessment.v3.school.dto;

public record V3SchoolProfileResponse(
        String schoolId,
        String schoolName,
        String contactNumber,
        String email,
        Address address
) {
    public record Address(
            String countryCode,
            String regionCode,
            String regionName,
            String provinceCode,
            String provinceName,
            String cityMunicipalityCode,
            String cityMunicipalityName,
            String barangayCode,
            String barangayName,
            String addressLine,
            String postalCode,
            String addressSource
    ) {
    }
}
