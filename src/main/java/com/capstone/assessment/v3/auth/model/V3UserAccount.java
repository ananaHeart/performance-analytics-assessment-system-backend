package com.capstone.assessment.v3.auth.model;

import java.time.Instant;
import java.time.LocalDate;

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
        // When the teacher verified their email or contact number (whichever they chose); null if neither.
        Instant verifiedAt,
        boolean mfaRequired,
        Instant createdAt,
        // REG-2: last valid day of a teacher's contract; null for principals and older accounts.
        LocalDate contractEndDate
) {
    public V3UserAccount(long userId, String schoolId, long addressId, String firstName, String middleName,
                         String lastName, String suffix, String email, String passwordHash, String role,
                         String status, int failedLoginCount, Instant lockedUntilAt, Instant verifiedAt,
                         boolean mfaRequired, Instant createdAt) {
        this(userId, schoolId, addressId, firstName, middleName, lastName, suffix, email, passwordHash, role,
                status, failedLoginCount, lockedUntilAt, verifiedAt, mfaRequired, createdAt, null);
    }

    /** REG-2: the contract ended before {@code today} (the last valid day has passed). */
    public boolean contractEndedBefore(LocalDate today) {
        return contractEndDate != null && contractEndDate.isBefore(today);
    }
}
