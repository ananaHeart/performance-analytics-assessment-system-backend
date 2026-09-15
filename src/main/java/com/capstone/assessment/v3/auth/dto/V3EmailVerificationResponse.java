package com.capstone.assessment.v3.auth.dto;

import java.time.Instant;

public record V3EmailVerificationResponse(
        String accountStatus,
        boolean emailVerified,
        String emailMasked,
        String challengeUuid,
        String deliveryStatus,
        Instant otpExpiresAt,
        Instant resendAvailableAt,
        Instant registrationExpiresAt
) {
}
