package com.capstone.assessment.v3.auth.model;

import java.time.Instant;

/**
 * A just-created registration code, sent by the channel the teacher chose: "email" or "sms".
 * The teacher enters this one code; it verifies that channel's address.
 */
public record V3PendingRegistrationChallenge(
        long challengeId,
        String challengeUuid,
        long userId,
        long addressId,
        String deliveryChannel,
        String recipientEmail,
        String recipientPhone,
        String destinationMasked,
        String rawOtp,
        int resendCount,
        Instant otpExpiresAt,
        Instant resendAvailableAt,
        Instant registrationExpiresAt
) {
    public boolean bySms() {
        return "sms".equals(deliveryChannel);
    }
}
