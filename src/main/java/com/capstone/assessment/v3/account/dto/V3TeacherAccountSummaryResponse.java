package com.capstone.assessment.v3.account.dto;

import java.time.Instant;
import java.time.LocalDate;

public record V3TeacherAccountSummaryResponse(
        long userId,
        String schoolId,
        String fullName,
        String email,
        String contactNumber,
        String status,
        Instant emailVerifiedAt,
        Instant createdAt,
        Instant updatedAt,
        String approvalStatus,
        String accountStatus,
        LocalDate contractEndDate,
        Instant archivedAt
) {
}
