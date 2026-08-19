package com.capstone.assessment.v2.auth.model;

import java.time.Instant;

public record V2LoginUser(
        Long userId,
        String schoolId,
        String firstName,
        String middleName,
        String lastName,
        String suffix,
        String email,
        String passwordHash,
        String role,
        String status,
        int failedLoginCount,
        Instant lockedUntilAt,
        Instant emailVerifiedAt
) {
}
