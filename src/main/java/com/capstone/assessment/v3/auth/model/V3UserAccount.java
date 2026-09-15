package com.capstone.assessment.v3.auth.model;

import java.time.Instant;

public record V3UserAccount(
        long userId,
        String schoolId,
        long addressId,
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
        Instant emailVerifiedAt,
        boolean mfaRequired,
        Instant createdAt
) {
}
