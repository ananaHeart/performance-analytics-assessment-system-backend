package com.capstone.assessment.v3.account.dto;

import java.time.Instant;
import java.time.LocalDate;

public record V3TeacherAccountDetailResponse(
        long userId,
        String schoolId,
        long addressId,
        int genderId,
        String genderName,
        Integer majorId,
        String majorName,
        Integer educationalAttainmentId,
        String educationalAttainmentName,
        Integer suffixId,
        String suffixName,
        String firstName,
        String middleName,
        String lastName,
        String fullName,
        LocalDate birthDate,
        Integer teachingStartMonth,
        Integer teachingStartYear,
        String email,
        String contactNumber,
        String role,
        String status,
        boolean emailVerified,
        boolean contactVerified,
        Instant emailVerifiedAt,
        Instant contactVerifiedAt,
        Instant createdAt,
        Instant updatedAt,
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
