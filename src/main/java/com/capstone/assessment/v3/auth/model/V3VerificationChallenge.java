package com.capstone.assessment.v3.auth.model;

import java.time.Instant;

public record V3VerificationChallenge(
        long challengeId,
        String challengeUuid,
        long userId,
        long addressId,
        String schoolId,
        String email,
        String contactNumber,
        String verificationPurpose,
        // "email" or "sms": where this code was sent.
        String deliveryChannel,
        // The masked email or phone number the code went to.
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
        boolean emailVerified,
        boolean contactVerified
) {
    public boolean bySms() {
        return "sms".equals(deliveryChannel);
    }
}
