package com.capstone.assessment.v3.school.dto;

import java.time.Instant;
import java.time.LocalDate;

public record V3StudentRosterEntryResponse(
        long classListId,
        String membershipUuid,
        long studentId,
        String studentLrn,
        String firstName,
        String middleName,
        String lastName,
        String suffixName,
        String fullName,
        String gender,
        LocalDate birthDate,
        String studentStatus,
        String enrollmentStatus,
        String enrollmentSource,
        Instant enrolledAt,
        Instant endedAt,
        String statusReason,
        Long statusChangedByUserId,
        Instant updatedAt
) {
}
