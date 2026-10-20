package com.capstone.assessment.v3.auth.dto;

import java.time.Instant;

public record V3EmailVerificationResponse(
        String accountStatus,
        String verificationMethod,
        boolean emailVerified,
        boolean contactVerified,
        // Set for email; null for sms.
        String emailMasked,
        // Set for sms; null for email.
        String contactMasked,
        String challengeUuid,
        String deliveryStatus,
        Instant otpExpiresAt,
        Instant resendAvailableAt,
        Instant registrationExpiresAt
) {
}
