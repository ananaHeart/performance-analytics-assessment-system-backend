package com.capstone.assessment.v3.auth.model;

import java.time.Instant;

public record V3PendingEmailChallenge(
        long challengeId,
        String challengeUuid,
        long userId,
        long addressId,
        String recipientEmail,
        String emailMasked,
        String rawOtp,
        int resendCount,
        Instant otpExpiresAt,
        Instant resendAvailableAt,
        Instant registrationExpiresAt
) {
}
