package com.capstone.assessment.v3.auth.model;

import java.time.Instant;

public record V3VerificationChallenge(
        long challengeId,
        String challengeUuid,
        long userId,
        long addressId,
        String schoolId,
        String email,
        String emailMasked,
        String codeHash,
        String challengeStatus,
        String deliveryStatus,
        int attemptCount,
        int maximumAttemptCount,
        int resendCount,
        Instant nextResendAt,
        Instant expiresAt,
        Instant userCreatedAt,
        String accountStatus,
        boolean emailVerified
) {
}
