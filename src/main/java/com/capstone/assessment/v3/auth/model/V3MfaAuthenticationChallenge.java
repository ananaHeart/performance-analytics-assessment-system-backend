package com.capstone.assessment.v3.auth.model;

import java.time.Instant;

public record V3MfaAuthenticationChallenge(
        long challengeId,
        String challengeUuid,
        long userId,
        long factorId,
        String status,
        int attemptCount,
        int maximumAttemptCount,
        Instant expiresAt,
        Instant verifiedAt,
        Instant createdAt
) {
}
