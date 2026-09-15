package com.capstone.assessment.v3.account.model;

import java.time.Instant;
import java.time.LocalDate;

public record V3TeacherAccount(
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
        LocalDate birthDate,
        Integer teachingStartMonth,
        Integer teachingStartYear,
        String email,
        String contactNumber,
        String role,
        String status,
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
