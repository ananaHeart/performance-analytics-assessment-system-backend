package com.capstone.assessment.v3.auth.model;

import java.time.Instant;

/** A just-created SMS challenge for the registering teacher's contact number. */
public record V3PendingContactChallenge(
        long challengeId,
        String challengeUuid,
        long userId,
        String contactNumber,
        String contactMasked,
        String rawCode,
        int resendCount,
        Instant codeExpiresAt,
        Instant resendAvailableAt
) {
}
