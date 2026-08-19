package com.capstone.assessment.v2.account.dto;

import java.time.Instant;
import java.time.LocalDate;

public record V2TeacherAccountResponse(
        Long userId,
        String schoolId,
        Long addressId,
        Integer genderId,
        Integer majorId,
        Integer educationalAttainmentId,
        String firstName,
        String middleName,
        String lastName,
        String suffix,
        LocalDate birthDate,
        LocalDate teachingStartDate,
        String email,
        String contactNumber,
        String role,
        String status,
        boolean emailVerified,
        boolean contactVerified,
        Instant createdAt,
        Instant updatedAt
) {
}
